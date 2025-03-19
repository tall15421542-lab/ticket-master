package lab.tall15421542.app.ticket;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.beans.AreaBean;
import lab.tall15421542.app.domain.beans.CreateReservationBean;
import lab.tall15421542.app.domain.beans.EventBean;
import lab.tall15421542.app.domain.beans.ReservationBean;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.streams.StreamsConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class ServiceTest {
    private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
    private static final Logger log = LoggerFactory.getLogger(ServiceTest.class);

    private static Service service1;
    private static Service service2;
    private static int port1 = 4099;
    private static int port2 = 4098;

    @Container
    private static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    private static KafkaConsumer<String, CreateEvent> createEventKafkaConsumer;
    private static KafkaConsumer<String, CreateReservation> createReservationKafkaConsumer;
    private static KafkaProducer<String, Reservation> reservationKafkaProducer;

    @BeforeAll
    static void setup() throws ExecutionException, InterruptedException, IOException {
        String bootstrapServers = kafka.getBootstrapServers();
        Properties props = new Properties();
        props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (Admin admin = Admin.create(props)) {
            int createEventTopicPartitions = 12;
            int createReservationTopiCPartitions = 20;
            int stateUserReservationPartitions = 30;
            short replicationFactor = 1;

            CreateTopicsResult result = admin.createTopics(Set.of(
                    new NewTopic(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name(), createEventTopicPartitions, replicationFactor),
                    new NewTopic(Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(), createReservationTopiCPartitions, replicationFactor),
                    new NewTopic(Schemas.Topics.STATE_USER_RESERVATION.name(), stateUserReservationPartitions, replicationFactor)
            ));

            KafkaFuture<Void> future = result.all();
            future.get();
        }

        props.setProperty(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(props);

        props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final int maxVirtualThreads = 128;
        Properties props1 = new Properties();
        props1.putAll(props);
        props1.setProperty(StreamsConfig.STATE_DIR_CONFIG, "./tmp/1");
        service1 = new Service("localhost", port1, maxVirtualThreads);
        service1.start(props1);

        Properties props2 = new Properties();
        props2.putAll(props);
        props2.setProperty(StreamsConfig.STATE_DIR_CONFIG, "./tmp/2");
        service2 = new Service("localhost", port2, maxVirtualThreads);
        service2.start(props2);

        Properties consumerProperties = new Properties();
        consumerProperties.putAll(props);
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.setProperty("enable.auto.commit", "false");
        consumerProperties.setProperty("auto.commit.interval.ms", "1000");
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.setProperty("group.id", "test-create-event");

        createEventKafkaConsumer = new KafkaConsumer<String, CreateEvent>(
                consumerProperties,
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.keySerde().deserializer(),
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.valueSerde().deserializer()
        );
        createEventKafkaConsumer.subscribe(Collections.singletonList(
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name())
        );

        consumerProperties.setProperty("group.id", "test-create-reservation");
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        createReservationKafkaConsumer = new KafkaConsumer<String, CreateReservation>(
                consumerProperties,
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.keySerde().deserializer(),
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.valueSerde().deserializer()
        );

        createReservationKafkaConsumer.subscribe(Collections.singletonList(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name()
        ));

        consumerProperties.setProperty("group.id", "test-reservation-state");
        reservationKafkaProducer = new KafkaProducer<>(
                consumerProperties,
                Schemas.Topics.STATE_USER_RESERVATION.keySerde().serializer(),
                Schemas.Topics.STATE_USER_RESERVATION.valueSerde().serializer()
        );
    }

    @Test
    void createEvent() throws InterruptedException {
        EventBean bean = new EventBean();
        bean.setEventName("mock-event-name");
        bean.setArtist("mock-artist");
        ArrayList<AreaBean> areas = new ArrayList<>();
        areas.add(new AreaBean("A", 100, 2, 3));
        bean.setAreas(areas);
        Invocation.Builder request = client.target(String.format("http://localhost:%d/v1/event", port1)).request(MediaType.APPLICATION_JSON);
        Response response = request.post(Entity.json(bean));
        assertEquals(200, response.getStatus());

        List actual = new CopyOnWriteArrayList<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> consumingTask = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, CreateEvent> records = createEventKafkaConsumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, CreateEvent> record : records) {
                    actual.add(record.value());
                }
            }
        });

        CreateEvent expected = bean.toAvro();

        try {
            Awaitility.await().atMost(10, SECONDS)
                    .until(() -> List.of(expected).equals(actual));
        } finally {
            consumingTask.cancel(true);
            executorService.awaitTermination(200, MILLISECONDS);
        }
    }

    @Test
    void createReservation() throws InterruptedException, ExecutionException {
        String userId = "mock-user-id";
        String eventId = "mock-event-id";
        String areaId = "A";
        int numOfSeat = 2;
        String type = "RANDOM";
        List<CreateReservationBean.SeatBean> seats = List.of(
                new CreateReservationBean.SeatBean(0,0),
                new CreateReservationBean.SeatBean(0,1)
        );
        CreateReservationBean req = new CreateReservationBean(
                userId, eventId, areaId, numOfSeat, seats, type
        );

        Invocation.Builder request = client.target(String.format("http://localhost:%d/v1/event/%s/reservation", port1, eventId)).request(MediaType.TEXT_PLAIN);
        Response response = request.post(Entity.json(req));
        assertEquals(200, response.getStatus());
        String reservationId = response.readEntity(String.class);

        List<ConsumerRecord<String, CreateReservation>> actual = new CopyOnWriteArrayList<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> consumingTask = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, CreateReservation> records = createReservationKafkaConsumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, CreateReservation> record : records) {
                    actual.add(record);
                }
            }
        });

        CreateReservation expected = req.toAvro();

        try {
            Awaitility.await().atMost(10, SECONDS)
                    .until(() -> actual.size() == 1 && actual.get(0).value().equals(expected));
        } finally {
            consumingTask.cancel(true);
            executorService.awaitTermination(200, MILLISECONDS);
        }

        List<Seat> reservedSeats = List.of(new Seat(0,0), new Seat(0,1));
        Reservation reservation = new Reservation(
                reservationId, userId, eventId, areaId, numOfSeat, numOfSeat, ReservationTypeEnum.RANDOM, reservedSeats, StateEnum.RESERVED, ""
        );

        ProducerRecord<String, Reservation> reservationStatusUpdated = new ProducerRecord<>(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                reservationId, reservation
        );
        reservationKafkaProducer.send(reservationStatusUpdated);

        Invocation.Builder request1 = client.target(String.format("http://localhost:%d/v1/reservation/%s", port1, reservationId)).request(MediaType.APPLICATION_JSON);
        Response response1 = request1.get();
        ReservationBean reservationBean = response1.readEntity(ReservationBean.class);
        assertEquals(ReservationBean.fromAvro(reservation), reservationBean);

        Invocation.Builder request2 = client.target(String.format("http://localhost:%d/v1/reservation/%s", port2, reservationId)).request(MediaType.APPLICATION_JSON);
        Response response2 = request2.get();
        assertEquals(200, response2.getStatus());
        ReservationBean reservation2 = response2.readEntity(ReservationBean.class);
        assertEquals(ReservationBean.fromAvro(reservation), reservation2);
    }

    @AfterAll
    static void close() throws Exception {
        createEventKafkaConsumer.close();
        createReservationKafkaConsumer.close();
        reservationKafkaProducer.close();
        service1.close();
    }
}
