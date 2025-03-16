package lab.tall15421542.app.ticket;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.container.AsyncResponse;
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
import org.apache.kafka.clients.producer.*;
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
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Path("v1")
public class Service extends Application {
    Producer<String, CreateEvent> createEventProducer;
    Producer<String, CreateReservation> createReservationProducer;
    private String hostname;
    private int port;
    private int maxVirtualThreads;
    KafkaStreams streams;
    final Map<String, AsyncResponse> outstandingRequests = new ConcurrentHashMap<>();
    final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();

    public Service(String hostname, int port, int maxVirtualThreads){
        this.hostname = hostname;
        this.port = port;
        this.maxVirtualThreads = maxVirtualThreads;
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
                        .longOpt("max-virtual-threads").hasArg().desc("Config file path").build())
                .addOption(Option.builder("h")
                        .longOpt("help").hasArg(false).desc("Show usage information").build());

        final CommandLine cl = new DefaultParser().parse(opts, args);
        if (cl.hasOption("h")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Ticket Service", opts);
            return;
        }

        final String restHostname = cl.getOptionValue("hostname", "localhost");
        final int restPort = Integer.parseInt(cl.getOptionValue("port", "4403"));
        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String configFile = cl.getOptionValue("config");
        final int maxVirtualThreads = Integer.parseInt(cl.getOptionValue("max-virtual-threads", "5000"));

        Properties config = Utils.readConfig(configFile);
        Schemas.configureSerdes(config);

        final Service service = new Service(restHostname, restPort, maxVirtualThreads);
        config.setProperty(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        service.start(config);

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        service.close();
    }

    public void start(Properties config){
        createEventProducer = startProducer(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT, config);
        createReservationProducer = startProducer(Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION, config);
        this.streams = startKafkaStream(config);

        startJetty(this.port, this.maxVirtualThreads, this);
    }

    public void close(){
        createEventProducer.close();
        createReservationProducer.close();
        this.streams.close();
    }

    public static <T> KafkaProducer startProducer(final Schemas.Topic<String, T> topic, final Properties defaultConfig) {
        final Properties producerConfig = new Properties();
        producerConfig.putAll(defaultConfig);
        producerConfig.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producerConfig.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.setProperty(ProducerConfig.LINGER_MS_CONFIG,
                defaultConfig.getProperty(ProducerConfig.LINGER_MS_CONFIG, "20"));
        producerConfig.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG,
                defaultConfig.getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none"));

        return new KafkaProducer<>(producerConfig,
                topic.keySerde().serializer(),
                topic.valueSerde().serializer());
    }

    private KafkaStreams startKafkaStream(Properties config){
        final Topology topology = createTopology();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.putAll(config);
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "ticket-service");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(StreamsConfig.APPLICATION_SERVER_CONFIG, this.hostname + ":" + this.port);
        props.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, RocksDBConfig.class);
        props.setProperty(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG");

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
                        .withValueSerde(Schemas.Stores.RESERVATION.valueSerde())
                        .withCachingDisabled());

        reservationTable.toStream().foreach((reservationId, reservation) -> {
            final AsyncResponse asyncResponse = outstandingRequests.get(reservationId);
            if (asyncResponse != null) {
                asyncResponse.resume(ReservationBean.fromAvro(reservation));
            }
        });

        return builder.build();
    }

    @GET
    @ManagedAsync
    @Path("/reservation/{reservation_id}")
    @Produces({MediaType.APPLICATION_JSON})
    public void getReservationById(@PathParam("reservation_id") final String reservationId,
                         @Suspended final AsyncResponse asyncResponse) {
        try{
            fetchReservation(asyncResponse, reservationId);
        } catch (final InvalidStateStoreException e) {
            outstandingRequests.put(reservationId, asyncResponse);
        }
    }

    @POST
    @ManagedAsync
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
        createEventProducer.send(record, (RecordMetadata metadata, Exception exception) -> {
            if(exception != null){
                asyncResponse.resume(exception);
            }
        });
        asyncResponse.resume(eventBean);
    }

    @POST
    @ManagedAsync
    @Path("/event/{id}/reservation")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.TEXT_PLAIN})
    public void createReservation(final CreateReservationBean createReservationBean,
                                  @Suspended final AsyncResponse asyncResponse){
        CreateReservation req = createReservationBean.toAvro();
        String reservationId = UUID.randomUUID().toString();
        ProducerRecord<String, CreateReservation> record = new ProducerRecord<>(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(), reservationId, req);
        createReservationProducer.send(record,((recordMetadata, e) -> {
            if(e != null){
                asyncResponse.resume(e);
            }
            asyncResponse.resume(reservationId);
        }));
    }

    private void fetchReservation(final AsyncResponse asyncResponse, final String reservationId) throws InvalidStateStoreException {
        // get key metadata
        // if it's in local, fetch from local
        // if it's in another host fetch from GET /reservation/{reservation_id} internal endpoint;
        HostInfo hostForKey = getKeyLocationOrBlock(reservationId, asyncResponse);
        if (hostForKey == null) { //request timed out so return
            asyncResponse.resume(Response.status(Response.Status.GATEWAY_TIMEOUT)
                    .entity("HTTP GET timed out after \n")
                    .build());
            return;
        }

        if(hostForKey.host().equals(this.hostname) && hostForKey.port() == this.port){
            fetchReservationFromLocal(reservationId, asyncResponse);
        }else{
            final String path = "http://" + hostForKey.host() + ":" + hostForKey.port() + "/v1/reservation/" + reservationId;
            fetchReservationFromOtherHost(path, asyncResponse);
        }
    }

    @WithSpan
    private void fetchReservationFromLocal(@SpanAttribute("reservation_id") String reservationId, AsyncResponse asyncResponse){
        Reservation reservation = reservationStore().get(reservationId);
        if(reservation == null){
            outstandingRequests.put(reservationId, asyncResponse);
            reservation = reservationStore().get(reservationId);
            if(reservation != null){
                outstandingRequests.remove(reservationId);
                asyncResponse.resume(ReservationBean.fromAvro(reservation));
            }
        }else{
            asyncResponse.resume(ReservationBean.fromAvro(reservation));
        }
    }

    private void fetchReservationFromOtherHost(String path, AsyncResponse asyncResponse) {
        System.out.println("Get from other host, path: " + path);
        try {
            final ReservationBean reservationBean = client.target(path)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(new GenericType<ReservationBean>() {
                    });
            asyncResponse.resume(reservationBean);
        } catch (final Exception swallowed) {
            System.out.println("GET failed."+ swallowed);
        }
    }

    @WithSpan
    private HostInfo getKeyLocationOrBlock(@SpanAttribute("reservation_id") final String id, final AsyncResponse asyncResponse) {
        HostInfo locationOfKey = null;
        while (locationOfKey == null) {
            try {
                KeyQueryMetadata metadata = this.streams.queryMetadataForKey(
                        Schemas.Stores.RESERVATION.name(),
                        id, Schemas.Stores.RESERVATION.keySerde().serializer()
                );

                if(metadata != KeyQueryMetadata.NOT_AVAILABLE){
                    locationOfKey = metadata.activeHost();
                    break;
                }
            } catch (final NotFoundException swallow) {
                // swallow
            }

            //The metastore is not available. This can happen on startup/rebalance.
            if (asyncResponse.isDone()) {
                //The response timed out so return
                return null;
            }
            try {
                //Sleep a bit until metadata becomes available
                Thread.sleep(Math.min(Long.parseLong("10000"), 200));
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

    public static Server startJetty(final int port, final int maxVirtualThreads, final Object binding) {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        QueuedThreadPool threadPool = new QueuedThreadPool();
        VirtualThreadPool virtualExecutor = new VirtualThreadPool();
        virtualExecutor.setMaxThreads(maxVirtualThreads);
        threadPool.setVirtualThreadsExecutor(virtualExecutor);

        final Server jettyServer = new Server(threadPool);
        jettyServer.setHandler(context);

        // The HTTP configuration object.
        HttpConfiguration httpConfig = new HttpConfiguration();

        // The ConnectionFactory for HTTP/1.1.
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

        // The ConnectionFactory for clear-text HTTP/2.
        HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);

        ServerConnector connector = new ServerConnector(jettyServer, http11, h2c);
        connector.setPort(port);
        jettyServer.addConnector(connector);

        final ResourceConfig rc = new ResourceConfig();
        rc.register(binding);
        rc.register(ObjectMapperWithTimeModule.class);
        rc.register(JacksonFeature.class);

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
