package lab.tall15421542.app.ticket;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.beans.EventBean;
import lab.tall15421542.app.domain.beans.CreateReservationBean;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.avro.event.CreateEvent;

import lab.tall15421542.app.domain.beans.ReservationBean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.jackson.JacksonFeature;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

@Path("v1")
public class Service {
    Producer<String, CreateEvent> createEventProducer;
    Producer<String, CreateReservation> createReservationProducer;
    private String hostname;
    private int port;
    KafkaStreams streams;
    final Map<String, AsyncResponse> outstandingRequests = new ConcurrentHashMap<>();
    final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();

    public Service(String hostname, int port){
        this.hostname = hostname;
        this.port = port;
    }

    public static void main(final String[] args) throws Exception {
        final Options opts = new Options();
        opts.addOption(Option.builder("b")
                        .longOpt("bootstrap-servers").hasArg().desc("Kafka cluster bootstrap server string").build())
                .addOption(Option.builder("s")
                        .longOpt("schema-registry").hasArg().desc("Schema Registry URL").build())
                .addOption(Option.builder("h")
                        .longOpt("hostname").hasArg().desc("This services HTTP host name").build())
                .addOption(Option.builder("p")
                        .longOpt("port").hasArg().desc("This services HTTP port").build())
                .addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("The directory for state storage").build())
                .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Show usage information").build());

        final CommandLine cl = new DefaultParser().parse(opts, args);
        if (cl.hasOption("h")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Ticket Service", opts);
            return;
        }

        final String bootstrapServers = cl.getOptionValue("bootstrap-servers", "localhost:29092,localhost:39092,localhost:49092");
        final String restHostname = cl.getOptionValue("hostname", "localhost");
        final int restPort = Integer.parseInt(cl.getOptionValue("port", "4403"));
        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String schemaRegistryUrl = cl.getOptionValue("schema-registry", "http://localhost:8081");

        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        Schemas.configureSerdes(config);

        final Service service = new Service(restHostname, restPort);

        config.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        service.start(bootstrapServers, config);

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        service.close();
    }

    public void start(String bootstrapServers, Properties config){
        createEventProducer = startProducer(bootstrapServers, Schemas.Topics.COMMAND_EVENT_CREATE_EVENT, config);
        createReservationProducer = startProducer(bootstrapServers, Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION, config);
        this.streams = startKafkaStream(bootstrapServers, config);

        startJetty(this.port, this);
    }

    public void close(){
        createEventProducer.close();
        createReservationProducer.close();
        this.streams.close();
    }

    private KafkaStreams startKafkaStream(String bootstrapServers, Properties config){
        final Topology topology = createTopology();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.putAll(config);
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "ticket-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, this.hostname + ":" + this.port);
        props.put(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG");

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

        KTable<String, Reservation> reservationTable = reservationStream.transform(() -> new ReservationTransformer()).toTable(
                Materialized.<String, Reservation, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.REQUEST_ID_RESERVATION.name())
                        .withKeySerde(Schemas.Stores.REQUEST_ID_RESERVATION.keySerde())
                        .withValueSerde(Schemas.Stores.REQUEST_ID_RESERVATION.valueSerde())
                        .withCachingDisabled());

        reservationTable.toStream().foreach((requestId, reservation) -> {
            final AsyncResponse asyncResponse = outstandingRequests.get(requestId);
            if (asyncResponse != null) {
                asyncResponse.resume(ReservationBean.fromAvro(reservation));
            }
        });

        return builder.build();
    }

    @GET
    @ManagedAsync
    @Path("/reservation/{request_id}")
    @Produces({MediaType.APPLICATION_JSON})
    public void getReservationByRequestId(@PathParam("request_id") final String requestId,
                         @Suspended final AsyncResponse asyncResponse) {
        try{
            fetchReservation(asyncResponse, requestId);
        } catch (final InvalidStateStoreException e) {
            outstandingRequests.put(requestId, asyncResponse);
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
    @Produces({MediaType.APPLICATION_JSON})
    public void createReservation(final CreateReservationBean createReservationBean,
                                  @Suspended final AsyncResponse asyncResponse){
        CreateReservation req = createReservationBean.toAvro();
        ProducerRecord<String, CreateReservation> record = new ProducerRecord<>(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(), req.getUserId().toString(), req);

        String requestId = UUID.randomUUID().toString();
        System.out.println("request-id: " + requestId);
        record.headers().add("request-id", requestId.getBytes(StandardCharsets.UTF_8));
        createReservationProducer.send(record, createReservationCallback(asyncResponse, requestId));
    }

    private Callback createReservationCallback(final AsyncResponse asyncResponse, final String requestId){
        return (recordMetadata, e) -> {
            if (e != null) {
                asyncResponse.resume(e);
            }

            try{
                fetchReservation(asyncResponse, requestId);
            } catch (final InvalidStateStoreException e2) {
                outstandingRequests.put(requestId, asyncResponse);
            }
        };
    }

    private void fetchReservation(final AsyncResponse asyncResponse, final String requestId) throws InvalidStateStoreException {
        // get key metadata
        // if it's in local, fetch from local
        // if it's in another host fetch from GET /reservation/request_id/{} internal endpoint;
        HostInfo hostForKey = getKeyLocationOrBlock(requestId, asyncResponse);
        if (hostForKey == null) { //request timed out so return
            asyncResponse.resume(Response.status(Response.Status.GATEWAY_TIMEOUT)
                    .entity("HTTP GET timed out after \n")
                    .build());
            return;
        }

        if(hostForKey.host().equals(this.hostname) && hostForKey.port() == this.port){
            fetchReservationFromLocal(requestId, asyncResponse);
        }else{
            final String path = "http://" + hostForKey.host() + ":" + hostForKey.port() + "/v1/reservation/" + requestId;
            fetchReservationFromOtherHost(path, asyncResponse);
        }
    }

    private void fetchReservationFromLocal(String requestId, AsyncResponse asyncResponse){
        final Reservation reservation = reservationStore().get(requestId);
        if(reservation == null){
            outstandingRequests.put(requestId, asyncResponse);
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

    private HostInfo getKeyLocationOrBlock(final String id, final AsyncResponse asyncResponse) {
        HostInfo locationOfKey = null;
        while (locationOfKey == null) {
            try {
                KeyQueryMetadata metadata = this.streams.queryMetadataForKey(
                        Schemas.Stores.REQUEST_ID_RESERVATION.name(),
                        id, Schemas.Stores.REQUEST_ID_RESERVATION.keySerde().serializer()
                );

                if(metadata != KeyQueryMetadata.NOT_AVAILABLE){
                    locationOfKey = metadata.activeHost();
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
                        Schemas.Stores.REQUEST_ID_RESERVATION.name(),
                        QueryableStoreTypes.keyValueStore()));
    }

    public static Server startJetty(final int port, final Object binding) {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        final Server jettyServer = new Server(port);
        jettyServer.setHandler(context);

        final ResourceConfig rc = new ResourceConfig();
        rc.register(binding);
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

    public static <T> KafkaProducer startProducer(final String bootstrapServers,
                                                  final Schemas.Topic<String, T> topic,
                                                  final Properties defaultConfig) {
        final Properties producerConfig = new Properties();
        producerConfig.putAll(defaultConfig);
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");

        return new KafkaProducer<>(producerConfig,
                topic.keySerde().serializer(),
                topic.valueSerde().serializer());
    }
}
