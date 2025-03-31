package lab.tall15421542.app.ticket;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.CompletionCallback;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.beans.CreateReservationBean;
import lab.tall15421542.app.domain.beans.EventBean;
import lab.tall15421542.app.domain.beans.ReservationBean;
import lab.tall15421542.app.utils.RocksDBConfig;
import lab.tall15421542.app.utils.Utils;
import org.apache.commons.cli.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;

import static lab.tall15421542.app.utils.Utils.addShutdownHookAndBlock;
import static org.glassfish.jersey.CommonProperties.USE_VIRTUAL_THREADS;

@Path("v1")
public class Service extends Application {
    Producer<String, CreateEvent> createEventProducer;
    Producer<String, CreateReservation> createReservationProducer;
    private String hostname;
    private int port;
    KafkaStreams streams;
    final Map<String, AsyncResponse> outstandingRequests = new ConcurrentHashMap<>();
    final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
    Server server;
    ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    final static Logger logger = LoggerFactory.getLogger(Service.class);
    final static String ENABLE_REQUEST_LOG = "enable.request.log";
    final static String MAX_THREADS = "max.threads";

    public Service(String hostname, int port){
        this.hostname = hostname;
        this.port = port;
    }

    public static void main(final String[] args) throws Exception {
        final Options opts = new Options();
        opts.addOption(Option.builder("h")
                        .longOpt("hostname").hasArg().desc("This services HTTP host name").build())
                .addOption(Option.builder("p")
                        .longOpt("port").hasArg().desc("This services HTTP port").build())
                .addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("The directory for state storage").build())
                .addOption(Option.builder("c")
                        .longOpt("config").hasArg().desc("Config file path").required().build())
                .addOption(Option.builder("n")
                        .longOpt("max-threads").hasArg().desc("Max threads created by jetty").build())
                .addOption(Option.builder("pc")
                        .longOpt("producer-config").hasArg().desc("Producer config file path").build())
                .addOption(Option.builder("sc")
                        .longOpt("stream-config").hasArg().desc("Stream config file path").build())
                .addOption(Option.builder("r")
                        .longOpt("request-logging").hasArg(false).desc("Enable request log").build());

        final CommandLine cl;
        try{
            cl = new DefaultParser().parse(opts, args);
        } catch (MissingOptionException e){
            logger.error("Missing command line options.");
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Ticket Service", opts);
            return;
        } catch (ParseException e) {
            logger.error("Parsing command line options failed.");
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Ticket Service", opts);
            return;
        }

        final String restHostname = cl.getOptionValue("hostname", "localhost");
        final int restPort = Integer.parseInt(cl.getOptionValue("port", "4403"));
        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String configFile = cl.getOptionValue("config");
        final String producerConfigFile = cl.getOptionValue("producer-config", "");
        final String streamConfigFile = cl.getOptionValue("stream-config", "");
        final int maxThreads = Integer.parseInt(cl.getOptionValue("max-threads", "0"));
        final boolean enableRequestLog = cl.hasOption("request-logging");

        Properties baseConfig = Utils.readConfig(configFile);
        Schemas.configureSerdes(baseConfig);

        Properties producerConfig = new Properties();
        producerConfig.putAll(baseConfig);
        if(!producerConfigFile.equals("")){
            producerConfig.putAll(Utils.readConfig(producerConfigFile));
        }

        final Service service = new Service(restHostname, restPort);

        Properties streamConfig = new Properties();
        streamConfig.putAll(baseConfig);
        if(!streamConfigFile.equals("")){
            streamConfig.putAll(Utils.readConfig(streamConfigFile));
        }
        streamConfig.setProperty(StreamsConfig.STATE_DIR_CONFIG, stateDir);

        Properties serverConfig = new Properties();
        serverConfig.put(ENABLE_REQUEST_LOG, enableRequestLog);
        serverConfig.put(MAX_THREADS, maxThreads);
        service.start(streamConfig, producerConfig, serverConfig);

        addShutdownHookAndBlock(() -> {
            try {
                service.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void start(Properties streamConfig, Properties producerConfig, Properties serverConfig){
        this.createEventProducer = startProducer(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT, producerConfig);
        this.createReservationProducer = startProducer(Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION, producerConfig);
        this.streams = startKafkaStream(streamConfig);

        this.server = startJetty(this.port, serverConfig, this);
    }

    public void close() throws Exception {
        this.virtualExecutor.close();
        createEventProducer.close();
        createReservationProducer.close();
        this.streams.close();
        this.server.stop();
        this.server.join();
        logger.info("Outstanding Requests: {}", outstandingRequests);
    }

    public static <T> KafkaProducer startProducer(final Schemas.Topic<String, T> topic, final Properties defaultConfig) {
        final Properties producerConfig = new Properties();
        producerConfig.putAll(defaultConfig);
        producerConfig.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producerConfig.setProperty(ProducerConfig.CLIENT_ID_CONFIG, topic.name());

        return new KafkaProducer<>(producerConfig,
                topic.keySerde().serializer(),
                topic.valueSerde().serializer());
    }

    private KafkaStreams startKafkaStream(Properties config){
        final Topology topology = createTopology();
        logger.info("{}", topology.describe());

        Properties props = new Properties();
        props.putAll(config);
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "ticket-service");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(StreamsConfig.APPLICATION_SERVER_CONFIG, this.hostname + ":" + this.port);
        props.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, RocksDBConfig.class);
        props.putIfAbsent(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "100");

        KafkaStreams streams = new KafkaStreams(topology, props);

        final CountDownLatch startLatch = new CountDownLatch(1);
        streams.setStateListener((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING && oldState != KafkaStreams.State.RUNNING) {
                startLatch.countDown();
            }
        });

        streams.start();

        try {
            if (!startLatch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Streams never finished rebalancing on startup");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return streams;
    }

    Topology createTopology(){
        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, Reservation> reservationStream = builder.stream(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                Consumed.with(
                        Schemas.Topics.STATE_USER_RESERVATION.keySerde(),
                        Schemas.Topics.STATE_USER_RESERVATION.valueSerde()
                ));

        KTable<String, Reservation> reservationTable = reservationStream.toTable(
                Materialized.<String, Reservation, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.RESERVATION.name())
                        .withKeySerde(Schemas.Stores.RESERVATION.keySerde())
                        .withValueSerde(Schemas.Stores.RESERVATION.valueSerde()));

        reservationTable.toStream().foreach((reservationId, reservation) -> {
            final AsyncResponse asyncResponse = outstandingRequests.remove(reservationId);
            if (asyncResponse != null && asyncResponse.isSuspended()) {
                virtualExecutor.submit(()-> {
                    asyncResponse.resume(ReservationBean.fromAvro(reservation));
                });
            }
        });

        return builder.build();
    }

    @GET
    @Path("/reservation/{reservation_id}")
    @Produces({MediaType.APPLICATION_JSON})
    public void getReservationById(@SpanAttribute("reservation_id") @PathParam("reservation_id") final String reservationId,
                         @Suspended final AsyncResponse asyncResponse) {
        asyncResponse.setTimeout(10, TimeUnit.SECONDS);

        asyncResponse.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                outstandingRequests.remove(reservationId);
            }
        });

        virtualExecutor.submit( () -> {
            try{
                fetchReservation(asyncResponse, reservationId);
            } catch(InvalidStateStoreException e){
                logger.info("Invalid State Store exception {}", e.getMessage());
                Response resp = Response.status(Response.Status.SERVICE_UNAVAILABLE)
                                .entity(e.getMessage())
                                .build();
                asyncResponse.resume(resp);
            } catch (Exception e) {
                asyncResponse.resume(e);
            }
        });
    }

    @GET
    @Path("health_check")
    public Response healthCheck() {
        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Path("/event")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public void createEvent(final EventBean eventBean,
                         @Suspended final AsyncResponse asyncResponse) {
        CreateEvent req = eventBean.toAvro();
        ProducerRecord<String, CreateEvent> record = new ProducerRecord<>(
            Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name(),
            req.getEventName().toString(), req
        );
        virtualExecutor.submit(() -> {
            try{
                createEventProducer.send(record);
                asyncResponse.resume(eventBean);
            }catch (Exception e){
                asyncResponse.resume(e);
            }
        });
    }

    @POST
    @Path("/event/{id}/reservation")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.TEXT_PLAIN})
    public void createReservation(final CreateReservationBean createReservationBean,
                                  @Suspended final AsyncResponse asyncResponse){
        CreateReservation req = createReservationBean.toAvro();
        String reservationId = UUID.randomUUID().toString();
        ProducerRecord<String, CreateReservation> record = new ProducerRecord<>(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(), reservationId, req);
        virtualExecutor.submit(() -> {
            try{
                createReservationProducer.send(record);
                asyncResponse.resume(reservationId);
            } catch (Exception e){
                asyncResponse.resume(e);
            }
        });
    }

    private void fetchReservation(final AsyncResponse asyncResponse, final String reservationId) throws InvalidStateStoreException {
        HostInfo hostForKey = getKeyLocationOrBlock(reservationId, asyncResponse);

        //request timed out so return
        if (hostForKey == null) {
            return;
        }

        // if it's in local, fetch from local
        // if it's in another host fetch from GET /v1/reservation/{reservation_id};
        if(hostForKey.host().equals(this.hostname) && hostForKey.port() == this.port){
            fetchReservationFromLocal(reservationId, asyncResponse);
        }else{
            fetchReservationFromOtherHost(hostForKey, reservationId, asyncResponse);
        }
    }

    @WithSpan
    private void fetchReservationFromLocal(@SpanAttribute("reservation_id") String reservationId, AsyncResponse asyncResponse){
        Reservation reservation = reservationStore().get(reservationId);
        if(reservation == null){
            outstandingRequests.put(reservationId, asyncResponse);

            // Ensure reservation does not arrive just after null check but before putting the request to outstandingRequest.
            reservation = reservationStore().get(reservationId);
            if(reservation != null){
                outstandingRequests.remove(reservationId);
                asyncResponse.resume(ReservationBean.fromAvro(reservation));
            }
        }else{
            asyncResponse.resume(ReservationBean.fromAvro(reservation));
        }
    }

    private void fetchReservationFromOtherHost(HostInfo hostForKey, String reservationId, AsyncResponse asyncResponse) {
        final String path = "http://" + hostForKey.host() + ":" + hostForKey.port() + "/v1/reservation/" + reservationId;
        try {
            final ReservationBean reservationBean = client.target(path)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(new GenericType<ReservationBean>() {
                    });
            asyncResponse.resume(reservationBean);
        } catch (final WebApplicationException e) {
            asyncResponse.resume(e);
        }
    }

    @WithSpan
    private HostInfo getKeyLocationOrBlock(@SpanAttribute("reservation_id") final String id, final AsyncResponse asyncResponse) {
        HostInfo locationOfKey = null;
        while (locationOfKey == null) {
            KeyQueryMetadata metadata = this.streams.queryMetadataForKey(
                    Schemas.Stores.RESERVATION.name(),
                    id, Schemas.Stores.RESERVATION.keySerde().serializer()
            );

            if(metadata != KeyQueryMetadata.NOT_AVAILABLE){
                locationOfKey = metadata.activeHost();
                break;
            }

            //The metastore is not available. This can happen on startup/rebalance.

            //The response timed out so return
            if (asyncResponse.isDone()) {
                return null;
            }

            try {
                //Sleep a bit until metadata becomes available
                Thread.sleep(200);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
        return locationOfKey;
    }

    private ReadOnlyKeyValueStore<String, Reservation> reservationStore() {
        return streams.store(
                StoreQueryParameters.fromNameAndType(
                        Schemas.Stores.RESERVATION.name(),
                        QueryableStoreTypes.keyValueStore()));
    }

    public static Server startJetty(final int port, final Properties config, final Object binding) {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        VirtualThreadPool threadPool = new VirtualThreadPool();
        int max_threads = (int) config.getOrDefault(MAX_THREADS, 0);
        threadPool.setMaxThreads(max_threads);

        final Server jettyServer = new Server(threadPool);
        jettyServer.setHandler(context);

        boolean enableRequestLog = (boolean) config.getOrDefault(ENABLE_REQUEST_LOG, false);
        if(enableRequestLog){
            jettyServer.setRequestLog(new CustomRequestLog(new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT));
        }

        // The HTTP configuration object.
        HttpConfiguration httpConfig = new HttpConfiguration();

        // The ConnectionFactory for HTTP/1.1.
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

        // The ConnectionFactory for clear-text HTTP/2.
        HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);

        httpConfig.setNotifyRemoteAsyncErrors(false);

        ServerConnector connector = new ServerConnector(jettyServer, http11, h2c);
        connector.setPort(port);
        jettyServer.addConnector(connector);

        final ResourceConfig rc = new ResourceConfig();
        rc.register(binding);
        rc.register(ObjectMapperWithTimeModule.class);
        rc.register(JacksonFeature.class);
        rc.property(USE_VIRTUAL_THREADS, true);

        final ServletContainer sc = new ServletContainer(rc);
        final ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");

        try {
            jettyServer.start();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return jettyServer;
    }
}
