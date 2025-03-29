package lab.tall15421542.app.ticket;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
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
import org.apache.commons.io.FileUtils;
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
import org.apache.kafka.streams.KafkaStreams;
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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

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
        props1.setProperty(StreamsConfig.STATE_DIR_CONFIG, "./tmp-test/1");
        props1.setProperty(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "20");
        service1 = new Service("localhost", port1, maxVirtualThreads);
        service1.start(props1, props1);

        Properties props2 = new Properties();
        props2.putAll(props);
        props2.setProperty(StreamsConfig.STATE_DIR_CONFIG, "./tmp-test/2");
        props2.setProperty(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "20");
        service2 = new Service("localhost", port2, maxVirtualThreads);
        service2.start(props2, props2);

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

        //TODO: add health check to ensure service is ready.
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
    }

    @Test
    void interactive_query(){
        String userId = "mock-user-id";
        String eventId = "mock-event-id";
        String areaId = "A";
        String reservationId = "test-interactive-query";
        int numOfSeat = 2;

        List<Seat> reservedSeats = List.of(new Seat(0,0), new Seat(0,1));
        Reservation reservation = new Reservation(
                reservationId, userId, eventId, areaId, numOfSeat, numOfSeat, ReservationTypeEnum.RANDOM, reservedSeats, StateEnum.RESERVED, ""
        );

        ProducerRecord<String, Reservation> reservationStatusUpdated = new ProducerRecord<>(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                reservationId, reservation
        );
        reservationKafkaProducer.send(reservationStatusUpdated);

        // Prevent unavailable service while rebalancing.
        RetryConfig retryConfig = RetryConfig.<Response>custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofSeconds(5))
                .retryOnResult(resp -> resp.getStatus() == 500)
                .build();

        Retry retry = Retry.of("test-interactive-query-retry", retryConfig);

        Invocation.Builder request1 = client.target(String.format("http://localhost:%d/v1/reservation/%s", port1, reservationId)).request(MediaType.APPLICATION_JSON);
        Supplier<Response> firstGetReservationSupplier = Retry.decorateSupplier(retry, () -> request1.get());

        Response response1 = firstGetReservationSupplier.get();
        assertNotNull(response1);
        assertEquals(200, response1.getStatus());

        ReservationBean reservationBean1 = response1.readEntity(ReservationBean.class);
        assertEquals(ReservationBean.fromAvro(reservation), reservationBean1);

        Invocation.Builder request2 = client.target(String.format("http://localhost:%d/v1/reservation/%s", port2, reservationId)).request(MediaType.APPLICATION_JSON);
        Supplier<Response> secondGetReservationSupplier = Retry.decorateSupplier(retry, () -> request2.get());
        Response response2 = secondGetReservationSupplier.get();

        assertNotNull(response2);
        assertEquals(200, response2.getStatus());
        ReservationBean reservationBean2 = response2.readEntity(ReservationBean.class);
        assertEquals(ReservationBean.fromAvro(reservation), reservationBean2);

        assertNull(service1.outstandingRequests.get(reservationId));
        assertNull(service2.outstandingRequests.get(reservationId));
    }

    @Test
    void Get_not_yet_ready_reservation(){
        String userId = "mock-user-id";
        String eventId = "mock-event-id";
        String areaId = "A";
        String reservationId = "test-interactive-query";
        int numOfSeat = 2;
        List<Seat> reservedSeats = List.of(new Seat(0,0), new Seat(0,1));
        String notReadyReservationId = "not-yet-ready-reservation-id";

        Reservation notReadyReservation = new Reservation(
                notReadyReservationId, userId, eventId, areaId, numOfSeat, numOfSeat, ReservationTypeEnum.RANDOM, reservedSeats, StateEnum.FAILED, ""
        );

        ProducerRecord<String, Reservation> reservationStatusUpdated = new ProducerRecord<>(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                notReadyReservationId, notReadyReservation
        );

        // Prevent unavailable service while rebalancing.
        RetryConfig retryConfig = RetryConfig.<Response>custom()
                .maxAttempts(15)
                .waitDuration(Duration.ofSeconds(1))
                .retryOnResult(resp -> resp.getStatus() == 500)
                .build();

        Retry retry = Retry.of("get-not-yet-ready-reservation-retry", retryConfig);

        try {
            Invocation.Builder rejectedRequest = client.target(String.format("http://localhost:%d/v1/reservation/%s", port1, notReadyReservationId)).request(MediaType.APPLICATION_JSON);

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
            CompletableFuture<Response> asyncResponse = retry.executeCompletionStage(
                            scheduler, () -> CompletableFuture.supplyAsync( () -> {
                                try{
                                    Future<Response> asyncResp = rejectedRequest.async().get();
                                    // Waiting reservation is updated in the state store.
                                    Thread.sleep(100);
                                    return asyncResp.get();
                                } catch (InterruptedException | ExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            }))
                    .toCompletableFuture();

            while(service1.streams.state() != KafkaStreams.State.RUNNING || service2.streams.state() != KafkaStreams.State.RUNNING){
                Thread.sleep(500);
            }

            assertTrue(service1.outstandingRequests.get(notReadyReservationId) != null
                    || service2.outstandingRequests.get(notReadyReservationId) != null);

            reservationKafkaProducer.send(reservationStatusUpdated);

            asyncResponse.join();
            assertNotNull(asyncResponse);
            assertEquals(200, asyncResponse.get().getStatus());

            ReservationBean rejectedReservationBean = asyncResponse.get().readEntity(ReservationBean.class);
            assertEquals(ReservationBean.fromAvro(notReadyReservation), rejectedReservationBean);

            assertNull(service1.outstandingRequests.get(notReadyReservationId));
            assertNull(service2.outstandingRequests.get(notReadyReservationId));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void health_check(){
        Invocation.Builder request1 = client.target(String.format("http://localhost:%d/v1/health_check", port1)).request();
        Response response1 = request1.get();
        assertEquals(200, response1.getStatus());

        Invocation.Builder request2 = client.target(String.format("http://localhost:%d/v1/health_check", port2)).request();
        Response response2 = request2.get();
        assertEquals(200, response2.getStatus());
    }

    @AfterAll
    static void close() throws Exception {
        createEventKafkaConsumer.close();
        createReservationKafkaConsumer.close();
        reservationKafkaProducer.close();
        service1.close();
        FileUtils.deleteDirectory(new File("./tmp-test"));
    }
}
