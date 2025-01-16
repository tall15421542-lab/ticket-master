package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.Area;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.SeatStatus;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.KeyValueStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;

import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.domain.Schemas;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

class ServiceTest {
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, CreateEvent> MockCreateEventReqs;
    private KeyValueStore<String, AreaStatus> MockAreaStatusStore;

    @BeforeEach
    void setUp() {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(config);

        Topology topology = Service.createTopology();
        testDriver = new TopologyTestDriver(topology);

        // setup test topics
        MockCreateEventReqs = testDriver.<String, CreateEvent>createInputTopic(
                Topics.COMMAND_EVENT_CREATE_EVENT.name(),
                Topics.COMMAND_EVENT_CREATE_EVENT.keySerde().serializer(),
                Topics.COMMAND_EVENT_CREATE_EVENT.valueSerde().serializer());

        MockAreaStatusStore = testDriver.getKeyValueStore(Schemas.Stores.AREA_STATUS.name());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void createEvent() {
        List<Area> areas = new ArrayList<>();
        areas.add(new Area("A", 1000, 10, 10));
        areas.add(new Area("B", 1500, 5, 5));
        CreateEvent req = new CreateEvent("Tony", "mockEvent", Instant.now(), Instant.now(), Instant.now(), Instant.now(), areas);

        MockCreateEventReqs.pipeInput("mockEvent", req);
        AreaStatus testAreaStatusA = MockAreaStatusStore.get("mockEvent#A");
        assertNotNull(testAreaStatusA);
        assertEquals("mockEvent", testAreaStatusA.getEventId().toString());
        assertEquals("A", testAreaStatusA.getAreaId().toString());
        assertEquals(1000, testAreaStatusA.getPrice());
        assertEquals(10, testAreaStatusA.getRowCount());
        assertEquals(10, testAreaStatusA.getColCount());
        assertEquals(100, testAreaStatusA.getAvailableSeats());
        assertEquals(10, testAreaStatusA.getSeats().size());
        assertEquals(10, testAreaStatusA.getSeats().get(0).size());

        for(int r = 0 ; r < testAreaStatusA.getRowCount() ; ++r){
            for(int c = 0 ; c < testAreaStatusA.getColCount() ; ++c){
                SeatStatus testSeatStatus = testAreaStatusA.getSeats().get(r).get(c);
                assertEquals(r, testSeatStatus.getRow());
                assertEquals(c, testSeatStatus.getCol());
                assertTrue(testSeatStatus.getIsAvailable());
            }
        }
    }
}