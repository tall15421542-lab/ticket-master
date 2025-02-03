package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.validation.Schema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {
    TopologyTestDriver testDriver;
    TestInputTopic<String, CreateReservation> MockCreateReservationRequests;
    TestInputTopic<String, ReservationResult> MockReservationResults;
    TestInputTopic<String, AreaStatus> MockAreaStatusUpdated;
    TestOutputTopic<String, ReserveSeat> MockReserveSeatRequests;
    TestOutputTopic<String, Reservation> MockReservationUpdated;
    KeyValueStore<String, AreaStatus> MockAreaStatusCache;
    KeyValueStore<String, Reservation> MockReservationStore;

    @BeforeEach
    void setUp() {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();

        Service reservationService = new Service();
        testDriver = new TopologyTestDriver(reservationService.createTopology());

        // setup test topics
        MockCreateReservationRequests = testDriver.<String, CreateReservation>createInputTopic(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(),
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.keySerde().serializer(),
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.valueSerde().serializer()
        );

        MockReservationResults = testDriver.<String, ReservationResult>createInputTopic(
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.name(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.keySerde().serializer(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.valueSerde().serializer()
        );

        MockAreaStatusUpdated = testDriver.<String, AreaStatus>createInputTopic(
                Schemas.Topics.STATE_EVENT_AREA_STATUS.name(),
                Schemas.Topics.STATE_EVENT_AREA_STATUS.keySerde().serializer(),
                Schemas.Topics.STATE_EVENT_AREA_STATUS.valueSerde().serializer()
        );

        MockReserveSeatRequests = testDriver.<String, ReserveSeat>createOutputTopic(
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.name(),
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde().deserializer(),
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde().deserializer()
        );

        MockReservationUpdated = testDriver.<String, Reservation>createOutputTopic(
                Schemas.Topics.STATE_USER_RESERVATION.name(),
                Schemas.Topics.STATE_USER_RESERVATION.keySerde().deserializer(),
                Schemas.Topics.STATE_USER_RESERVATION.valueSerde().deserializer()
        );

        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        MockAreaStatusUpdated.pipeInput("event#A", areaStatus);

        MockAreaStatusCache = testDriver.getKeyValueStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name());
        assertNotNull(MockAreaStatusCache);

        AreaStatus cachedAreaStatus = MockAreaStatusCache.get("event#A");
        assertNotNull(cachedAreaStatus);
        assertEquals(areaStatus, cachedAreaStatus);

        MockReservationStore = testDriver.getKeyValueStore(Schemas.Stores.RESERVATION.name());
        assertNotNull(MockReservationStore);
    }

    @Test
    void SuccessfulContinuousRandomReservation(){
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );
        MockCreateReservationRequests.pipeInput("userId", req);
        KeyValue<String, ReserveSeat> reserveSeatReq = MockReserveSeatRequests.readKeyValue();

        ReserveSeat expectedReserveSeatRequest = new ReserveSeat(
                reserveSeatReq.value.getReservationId(), "event", "A",
                3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );
        assertEquals("event#A", reserveSeatReq.key);
        assertEquals(expectedReserveSeatRequest, reserveSeatReq.value);

        String reservationId = reserveSeatReq.value.getReservationId().toString();
        Reservation expectedReservation = new Reservation(
                reservationId, "userId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM,
                new ArrayList<>(),
                StateEnum.PROCESSING, ""
        );

        Reservation reservation = MockReservationStore.get(reservationId);
        assertNotNull(reservation);
        assertEquals(expectedReservation, reservation);

        assertTrue(MockReservationUpdated.isEmpty());

        ReservationResult reservationResult = new ReservationResult(
                reservationId, ReservationResultEnum.SUCCESS, Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2)),
                null, null
        );

        MockReservationResults.pipeInput(reservationId, reservationResult);

        expectedReservation.setState(StateEnum.RESERVED);
        expectedReservation.setSeats(Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2)));
        assertEquals(expectedReservation, MockReservationStore.get(reservationId));

        KeyValue<String, Reservation> reservationUpdatedKV = MockReservationUpdated.readKeyValue();
        assertEquals(reservationId, reservationUpdatedKV.key);
        assertEquals(expectedReservation, reservationUpdatedKV.value);
    }

    @Test
    void SuccessfulSelfPickRandomReservation(){
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.SELF_PICK,
                Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2))
        );
        MockCreateReservationRequests.pipeInput("userId", req);
        KeyValue<String, ReserveSeat> reserveSeatReq = MockReserveSeatRequests.readKeyValue();

        ReserveSeat expectedReserveSeatRequest = new ReserveSeat(
                reserveSeatReq.value.getReservationId(), "event", "A",
                3, 3, ReservationTypeEnum.SELF_PICK, Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2))
        );
        assertEquals("event#A", reserveSeatReq.key);
        assertEquals(expectedReserveSeatRequest, reserveSeatReq.value);

        String reservationId = reserveSeatReq.value.getReservationId().toString();
        Reservation expectedReservation = new Reservation(
                reservationId, "userId", "event", "A", 3, 3, ReservationTypeEnum.SELF_PICK,
                Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2)),
                StateEnum.PROCESSING, ""
        );

        Reservation reservation = MockReservationStore.get(reservationId);
        assertNotNull(reservation);
        assertEquals(expectedReservation, reservation);

        assertTrue(MockReservationUpdated.isEmpty());

        ReservationResult reservationResult = new ReservationResult(
                reservationId, ReservationResultEnum.SUCCESS, Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2)),
                null, null
        );

        MockReservationResults.pipeInput(reservationId, reservationResult);

        expectedReservation.setState(StateEnum.RESERVED);
        expectedReservation.setSeats(Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2)));
        assertEquals(expectedReservation, MockReservationStore.get(reservationId));

        KeyValue<String, Reservation> reservationUpdatedKV = MockReservationUpdated.readKeyValue();
        assertEquals(reservationId, reservationUpdatedKV.key);
        assertEquals(expectedReservation, reservationUpdatedKV.value);
    }
}