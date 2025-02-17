package lab.tall15421542.app.ticket;

import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.reservation.Service;
import org.apache.kafka.clients.admin.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import javax.swing.*;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Testcontainers
class ServiceTest {
    private static final Logger log = LoggerFactory.getLogger(ServiceTest.class);
    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0")).withStartupTimeout(Duration.ofMinutes(2)).withLogConsumer(new Slf4jLogConsumer(log));
    ;
    @BeforeAll
    static void setup() throws ExecutionException, InterruptedException{
        String bootstrapServers = kafka.getBootstrapServers();
        System.out.println(bootstrapServers);

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        AdminClient adminClient = KafkaAdminClient.create(props);

        NewTopic reservationState = new NewTopic(Schemas.Topics.STATE_USER_RESERVATION.name(), 20, (short) 1);
        NewTopic createEvent = new NewTopic(Schemas.Topics.COMMAND_EVENT_CREATE_EVENT.name(), 20, (short)1);
        CreateTopicsResult result = adminClient.createTopics(Set.of(reservationState, createEvent), new CreateTopicsOptions());
        result.all().get();

        System.out.println(adminClient.listTopics(new ListTopicsOptions()));
    }

    @Test
    void createReservation() throws InterruptedException {

    }
}