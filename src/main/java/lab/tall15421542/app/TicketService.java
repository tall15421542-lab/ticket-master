package lab.tall15421542.app;

import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.beans.EventBean;
import lab.tall15421542.app.domain.beans.ReservationBean;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.avro.event.CreateEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
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

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

@Path("v1")
public class TicketService {
    private KafkaProducer<String, CreateEvent> createEventProducer;
    private KafkaProducer<String, CreateReservation> CreateReservationProducer;

    public static void main(final String[] args) throws Exception {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final TicketService service = new TicketService();
        service.start("localhost:29092,localhost:39092,localhost:49092", config);

        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, Reservation> reservationStream = builder.stream(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                Consumed.with(
                        Schemas.Topics.STATE_USER_RESERVATION.keySerde(),
                        Schemas.Topics.STATE_USER_RESERVATION.valueSerde()
                ));

        reservationStream.transform(() -> new ReservationTransformer()).foreach(
                (requestId, reservation) -> System.out.println(requestId + ": " + reservation)
        );
        final Topology topology = builder.build();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "ticket-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        streams.close();
    }

    public void start(String bootstrapServers, Properties config){
        createEventProducer = startProducer(bootstrapServers, Schemas.Topics.COMMAND_EVENT_CREATE_EVENT, config);
        CreateReservationProducer = startProducer(bootstrapServers, Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION, config);
        startJetty(4403, this);
    }

    private static class ReservationTransformer implements Transformer<String, Reservation, KeyValue<String, Reservation>> {
        private ProcessorContext context;
        @Override
        public void init(ProcessorContext context){
            this.context = context;
        }

        @Override
        public KeyValue<String, Reservation> transform(String reservationId, Reservation reservation){
           String requestId = new String(this.context.headers().lastHeader("request-id").value());
           return KeyValue.pair(requestId, reservation);
        }

        @Override
        public void close(){

        }
    }

    @GET
    @ManagedAsync
    @Path("/event/{id}")
    @Produces({MediaType.TEXT_PLAIN})
    public void getEvent(@PathParam("id") final String id,
                          @Suspended final AsyncResponse asyncResponse) {
        asyncResponse.resume(id);
    }

    @POST
    @ManagedAsync
    @Path("/event")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public void createEvent(final EventBean eventBean,
                         @Suspended final AsyncResponse asyncResponse) {
        CreateEvent req = eventBean.toAvro();
        createEventProducer.send(new ProducerRecord<String, CreateEvent>(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name(), req.getEventName().toString(), req));
        asyncResponse.resume(eventBean);
    }

    @POST
    @ManagedAsync
    @Path("/event/{id}/reservation")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public void createReservation(final ReservationBean reservationBean,
                                  @Suspended final AsyncResponse asyncResponse){
        CreateReservation req = reservationBean.toAvro();
        ProducerRecord<String, CreateReservation> record = new ProducerRecord<>(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(), req.getUserId().toString(), req);

        String requestId = UUID.randomUUID().toString();
        System.out.println("request-id: " + requestId);
        record.headers().add("request-id", requestId.getBytes(StandardCharsets.UTF_8));
        CreateReservationProducer.send(record);

        asyncResponse.resume(reservationBean);
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
        producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, "create-event-sender");

        return new KafkaProducer<>(producerConfig,
                topic.keySerde().serializer(),
                topic.valueSerde().serializer());
    }
}
