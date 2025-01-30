package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;
class ReservationTransformerTest {
    TopologyTestDriver testDriver;
    TestInputTopic<String, CreateReservation> MockCreateReservationReqs;
    TestOutputTopic<String, Reservation> MockReservations;
    KeyValueStore<String, ValueAndTimestamp<AreaStatus>> eventAreaStatusCache;

    @BeforeEach
    void setup(){
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(config);

        StreamsBuilder builder = new StreamsBuilder();

        builder.globalTable(
                Schemas.Topics.STATE_EVENT_AREA_STATUS.name(),
                Materialized.<String, AreaStatus>as(
                                Stores.inMemoryKeyValueStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name())
                        )
                        .withKeySerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.keySerde())
                        .withValueSerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.valueSerde())
                        .withLoggingDisabled()
        );

        KStream<String, CreateReservation> reqs = builder.stream(Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(),
                Consumed.with(
                        Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.keySerde(),
                        Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.valueSerde()
                ));

        reqs.transform(ReservationTransformer::new).to("reservation", Produced.with(
                Schemas.Stores.RESERVATION.keySerde(),
                Schemas.Stores.RESERVATION.valueSerde()
        ));

        testDriver = new TopologyTestDriver(builder.build());

        MockCreateReservationReqs = testDriver.createInputTopic(
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(),
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.keySerde().serializer(),
                Schemas.Topics.COMMAND_RESERVATION_CREATE_RESERVATION.valueSerde().serializer()
        );

        MockReservations = testDriver.createOutputTopic(
                "reservation",
                Schemas.Stores.RESERVATION.keySerde().deserializer(),
                Schemas.Stores.RESERVATION.valueSerde().deserializer()
        );

        // setup initial area status
        eventAreaStatusCache = testDriver.getTimestampedKeyValueStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name());
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        eventAreaStatusCache.put("event#A", ValueAndTimestamp.make(areaStatus, Instant.now().toEpochMilli()));
    }

    @Test
    void PassContinuousRandomFilterAndCreateProcessingReservation(){
        MockCreateReservationReqs.pipeInput("userId", new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        ));

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "A", 3, 3,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.PROCESSING, "");

        KeyValue<String, Reservation> result = MockReservations.readKeyValue();
        assertNotNull(result.key);
        expectedReservation.setReservationId(result.value.getReservationId());
        assertEquals(expectedReservation, result.value);
    }

    @Test
    void BlockedByContinuousRandomFilterAndCreateFailedReservation(){
        MockCreateReservationReqs.pipeInput("userId", new CreateReservation(
                "userId", "event", "A", 4, 4, ReservationTypeEnum.RANDOM, new ArrayList<>()
        ));

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "A", 4, 4,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.FAILED, "request rejected at cache level");

        KeyValue<String, Reservation> result = MockReservations.readKeyValue();
        assertNotNull(result.key);
        expectedReservation.setReservationId(result.value.getReservationId());
        assertEquals(expectedReservation, result.value);
    }

    @Test
    void NonExisitingEventAreaAndCreateProcessingReservation(){
        MockCreateReservationReqs.pipeInput("userId", new CreateReservation(
                "userId", "event", "B", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        ));

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "B", 3, 3,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.PROCESSING, "");

        KeyValue<String, Reservation> result = MockReservations.readKeyValue();
        assertNotNull(result.key);
        expectedReservation.setReservationId(result.value.getReservationId());
        assertEquals(expectedReservation, result.value);
    }

    @Test
    void InvalidFilterAndCreateFailedReservation(){
        MockCreateReservationReqs.pipeInput("userId", new CreateReservation(
                "userId", "event", "A", 4, 4, ReservationTypeEnum.INVALID, new ArrayList<>()
        ));

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "A", 4, 4,
                ReservationTypeEnum.INVALID, new ArrayList<>(), StateEnum.FAILED, "INVALID type reservation is not supported");

        KeyValue<String, Reservation> result = MockReservations.readKeyValue();
        assertNotNull(result.key);
        expectedReservation.setReservationId(result.value.getReservationId());
        assertEquals(expectedReservation, result.value);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }
}