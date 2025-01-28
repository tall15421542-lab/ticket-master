package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.api.MockProcessorContext;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

class ReserveSeatTransformerTest {
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, ReserveSeat> MockReserveSeatReqs;
    private TestOutputTopic<String, ReservationResult> MockReservationResults;

    @BeforeEach
    void setUp() {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(Stores.timestampedKeyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(Schemas.Stores.AREA_STATUS.name()),
                Schemas.Stores.AREA_STATUS.keySerde(),
                Schemas.Stores.AREA_STATUS.valueSerde()
        ).withLoggingDisabled());

        KStream<String, ReserveSeat> requests = builder.stream(
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.name(),
                Consumed.with(
                        Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde(),
                        Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde()
                ));

        KStream<String, ReservationResult> reserveResults = requests.transform(ReserveSeatTransformer::new, Schemas.Stores.AREA_STATUS.name());
        reserveResults.to(Schemas.Topics.RESPONSE_RESERVATION_RESULT.name(), Produced.with(
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.keySerde(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.valueSerde()
        ));

        testDriver = new TopologyTestDriver(builder.build());

        // setup test topics
        MockReserveSeatReqs = testDriver.<String, ReserveSeat>createInputTopic(
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.name(),
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde().serializer(),
                Schemas.Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde().serializer());

        MockReservationResults = testDriver.<String, ReservationResult>createOutputTopic(
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.name(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.keySerde().deserializer(),
                Schemas.Topics.RESPONSE_RESERVATION_RESULT.valueSerde().deserializer()
        );
    }

    @Test
    void SuccessfulContinousRandomReservation(){
        KeyValueStore<String, AreaStatus> areaStatusStore = testDriver.getKeyValueStore(Schemas.Stores.AREA_STATUS.name());
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        areaStatusStore.put("event#A", areaStatus);

        ReserveSeat req = new ReserveSeat("reservationId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<Seat>());
        MockReserveSeatReqs.pipeInput("event#A", req);

        KeyValue<String, ReservationResult> result = MockReservationResults.readKeyValue();
        KeyValue<String, ReservationResult> expected = new KeyValue<>("reservationId", new ReservationResult(
                "reservationId", ReservationResultEnum.SUCCESS,
                Arrays.asList(new Seat(0,0), new Seat(0, 1), new Seat(0,2)), null, null
        ));
        assertEquals(expected, result);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }
}