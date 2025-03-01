package lab.tall15421542.app.ticket;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.beans.AreaBean;
import lab.tall15421542.app.domain.beans.EventBean;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.streams.StreamsConfig;
import org.checkerframework.checker.units.qual.A;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class ServiceTest {
    private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
    private static final Logger log = LoggerFactory.getLogger(ServiceTest.class);

    private static Service service;
    private static int port = 4099;

    @Container
    private static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    private static KafkaConsumer<String, CreateEvent> createEventKafkaConsumer;
    private static KafkaConsumer<String, ReserveSeat> reserveSeatKafkaConsumer;
    private static KafkaProducer<String, Reservation> reservationKafkaProducer;

    @BeforeAll
    static void setup() throws ExecutionException, InterruptedException, IOException {
        String bootstrapServers = kafka.getBootstrapServers();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (Admin admin = Admin.create(props)) {
            int createEventTopicPartitions = 12;
            int reserveSeatTopiCPartitions = 20;
            int stateUserReservationPartitions = 30;
            short replicationFactor = 1;

            CreateTopicsResult result = admin.createTopics(Set.of(
                    new NewTopic(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name(), createEventTopicPartitions, replicationFactor),
                    new NewTopic(Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.name(), reserveSeatTopiCPartitions, replicationFactor),
                    new NewTopic(Schemas.Topics.STATE_USER_RESERVATION.name(), stateUserReservationPartitions, replicationFactor)
            ));

            KafkaFuture<Void> future = result.all();
            future.get();
        }

        props.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(props);

        service = new Service("localhost", port);
        service.start(bootstrapServers, props);

        Properties consumerProperties = new Properties(props);
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.setProperty("group.id", "test");
        consumerProperties.setProperty("enable.auto.commit", "true");
        consumerProperties.setProperty("auto.commit.interval.ms", "1000");
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        createEventKafkaConsumer = new KafkaConsumer<String, CreateEvent>(
                consumerProperties,
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.keySerde().deserializer(),
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.valueSerde().deserializer()
        );
        createEventKafkaConsumer.subscribe(Collections.singletonList(
                Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name())
        );

        reserveSeatKafkaConsumer = new KafkaConsumer<String, ReserveSeat>(
                consumerProperties,
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde().deserializer(),
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde().deserializer()
        );

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
        Invocation.Builder request = client.target(String.format("http://localhost:%d/v1/event", port)).request(MediaType.APPLICATION_JSON);
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

    @AfterAll
    static void close(){
        createEventKafkaConsumer.close();
        reserveSeatKafkaConsumer.close();
        reservationKafkaProducer.close();

    }
}