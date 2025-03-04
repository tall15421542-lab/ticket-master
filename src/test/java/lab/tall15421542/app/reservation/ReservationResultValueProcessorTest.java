package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

class ReservationResultValueProcessorTest {
    TopologyTestDriver testDriver;
    TestInputTopic<String, ReservationResult> MockReservationResults;
    TestOutputTopic<String, Reservation> MockReservations;
    KeyValueStore<String, ValueAndTimestamp<Reservation>> reservationStore;

    @BeforeEach
    void setup(){
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(config);

        StreamsBuilder builder = new StreamsBuilder();

        builder.addStateStore(Stores.timestampedKeyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(Schemas.Stores.RESERVATION.name()),
                Schemas.Stores.RESERVATION.keySerde(),
                Schemas.Stores.RESERVATION.valueSerde()
        ).withLoggingDisabled());

        KStream<String, ReservationResult> responses = builder.stream(
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.name(),
                Consumed.with(
                        Schemas.Topics.RESPONSE_RESERVATION_RESULT.keySerde(),
                        Schemas.Topics.RESPONSE_RESERVATION_RESULT.valueSerde()
                )
        );

        responses.processValues(ReservationResultValueProcessor::new, Schemas.Stores.RESERVATION.name())
                .to("reservation", Produced.with(
                   Schemas.Stores.RESERVATION.keySerde(),
                   Schemas.Stores.RESERVATION.valueSerde()
                ));

        testDriver = new TopologyTestDriver(builder.build());
        MockReservationResults = testDriver.createInputTopic(
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.name(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.keySerde().serializer(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.valueSerde().serializer()
        );

        MockReservations = testDriver.createOutputTopic(
                "reservation",
                Schemas.Stores.RESERVATION.keySerde().deserializer(),
                Schemas.Stores.RESERVATION.valueSerde().deserializer()
        );

        reservationStore = testDriver.getTimestampedKeyValueStore(Schemas.Stores.RESERVATION.name());
        Reservation reservation = new Reservation(
                "reservationId", "userId", "event", "A", 3, 3,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.PROCESSING, "");
        reservationStore.put("reservationId", ValueAndTimestamp.make(reservation, Instant.now().toEpochMilli()));
    }

    @Test
    void transformSuccessfulReservation(){
        ReservationResult result = new ReservationResult("reservationId", ReservationResultEnum.SUCCESS, Arrays.asList(
                new Seat(0,0), new Seat(0,1), new Seat(0,2)), null, null
        );

        MockReservationResults.pipeInput("reservationId", result);
        KeyValue<String, Reservation> updatedReservation = MockReservations.readKeyValue();

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "A", 3, 3,
                ReservationTypeEnum.RANDOM, Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2))
                , StateEnum.RESERVED, "");

        assertEquals("reservationId", updatedReservation.key);
        assertEquals(expectedReservation, updatedReservation.value);

        ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get("reservationId");
        Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
        assertNotNull(reservation);
        assertEquals(expectedReservation, reservation);
    }

    @Test
    void transformFailedReservation(){
        ReservationResult result = new ReservationResult("reservationId", ReservationResultEnum.FAILED,
                new ArrayList<>(), ReservationErrorCodeEnum.NOT_AVAILABLE, "no continuous 3 seats at area A in event event"
        );

        MockReservationResults.pipeInput("reservationId", result);
        KeyValue<String, Reservation> updatedReservation = MockReservations.readKeyValue();

        Reservation expectedReservation = new Reservation(
                "reservationId", "userId", "event", "A", 3, 3,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.FAILED, "[NOT_AVAILABLE]: no continuous 3 seats at area A in event event");

        assertEquals("reservationId", updatedReservation.key);
        assertEquals(expectedReservation, updatedReservation.value);

        ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get("reservationId");
        Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
        assertNotNull(reservation);
        assertEquals(expectedReservation, reservation);
    }

    @Test
    void transformNonExistingReservation(){
        ReservationResult result = new ReservationResult("non-existing", ReservationResultEnum.FAILED,
                new ArrayList<>(), ReservationErrorCodeEnum.NOT_AVAILABLE, "no continuous 3 seats at area A in event event"
        );

        MockReservationResults.pipeInput("non-existing", result);
        KeyValue<String, Reservation> updatedReservation = MockReservations.readKeyValue();

        assertEquals("non-existing", updatedReservation.key);
        assertNull(updatedReservation.value);

        Reservation originReservation = new Reservation(
                "reservationId", "userId", "event", "A", 3, 3,
                ReservationTypeEnum.RANDOM, new ArrayList<>(), StateEnum.PROCESSING, "");
        ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get("reservationId");
        Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
        assertNotNull(reservation);
        assertEquals(originReservation, reservation);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }
}