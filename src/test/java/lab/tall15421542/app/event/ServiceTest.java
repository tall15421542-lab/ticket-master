package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.*;
import lab.tall15421542.app.avro.reservation.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.state.KeyValueStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.domain.Schemas;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

class ServiceTest {
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, CreateEvent> MockCreateEventReqs;
    private TestInputTopic<String, ReserveSeat> MockReserveSeatReqs;
    private TestOutputTopic<String, ReservationResult> MockReserveSeatResults;
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

        MockReserveSeatReqs = testDriver.<String, ReserveSeat>createInputTopic(
                Topics.COMMAND_EVENT_RESERVE_SEAT.name(),
                Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde().serializer(),
                Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde().serializer()
        );

        MockReserveSeatResults = testDriver.createOutputTopic(
                Topics.RESPONSE_RESERVATION_RESULT.name(),
                Topics.RESPONSE_RESERVATION_RESULT.keySerde().deserializer(),
                Topics.RESPONSE_RESERVATION_RESULT.valueSerde().deserializer()
        );

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

    @Test
    void successfulReservation() {
        List<Area> areas = new ArrayList<>();
        areas.add(new Area("A", 1000, 3, 3));
        areas.add(new Area("B", 1500, 5, 5));
        CreateEvent req = new CreateEvent("Tony", "mockEvent", Instant.now(), Instant.now(), Instant.now(), Instant.now(), areas);

        MockCreateEventReqs.pipeInput("mockEvent", req);
        MockReserveSeatReqs.pipeInput("mockEvent#A", new ReserveSeat(
                "reservationId", "mockEvent", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        ));

        KeyValue<String, ReservationResult> result = MockReserveSeatResults.readKeyValue();
        assertEquals("reservationId", result.key);

        ReservationResult expectedResult = new ReservationResult(
                "reservationId", ReservationResultEnum.SUCCESS,
                Arrays.asList(new Seat(0,0), new Seat(0, 1), new Seat(0,2)), null, null
        );
        assertEquals(expectedResult, result.value);

        AreaStatus currentAreaStatus = MockAreaStatusStore.get("mockEvent#A");
        assertNotNull(currentAreaStatus);

        assertEquals(6, currentAreaStatus.getAvailableSeats());
        for(Seat seat: result.value.getSeats()){
            assertFalse(currentAreaStatus.getSeats().get(seat.getRow()).get(seat.getCol()).getIsAvailable());
        }

        for(int row = 1 ; row < 3 ; ++row){
            for(int col = 0 ; col < 3 ; ++col){
                assertTrue(currentAreaStatus.getSeats().get(row).get(col).getIsAvailable());
            }
        }
    }

    @Test
    void FailedReservation() {
        List<Area> areas = new ArrayList<>();
        areas.add(new Area("A", 1000, 3, 3));
        areas.add(new Area("B", 1500, 5, 5));
        CreateEvent req = new CreateEvent("Tony", "mockEvent", Instant.now(), Instant.now(), Instant.now(), Instant.now(), areas);

        MockCreateEventReqs.pipeInput("mockEvent", req);
        MockReserveSeatReqs.pipeInput("mockEvent#A", new ReserveSeat(
                "reservationId", "mockEvent", "A", 4, 4, ReservationTypeEnum.RANDOM, new ArrayList<>()
        ));

        KeyValue<String, ReservationResult> result = MockReserveSeatResults.readKeyValue();
        assertEquals("reservationId", result.key);

        ReservationResult expectedResult = new ReservationResult(
                "reservationId", ReservationResultEnum.FAILED,
                null, ReservationErrorCodeEnum.NOT_AVAILABLE, "no continuous 4 seats at area A in event mockEvent"
        );
        assertEquals(expectedResult, result.value);

        AreaStatus currentAreaStatus = MockAreaStatusStore.get("mockEvent#A");
        assertNotNull(currentAreaStatus);

        assertEquals(9, currentAreaStatus.getAvailableSeats());
        for(int row = 0 ; row < 3 ; ++row){
            for(int col = 0 ; col < 3 ; ++col){
                assertTrue(currentAreaStatus.getSeats().get(row).get(col).getIsAvailable());
            }
        }
    }
}