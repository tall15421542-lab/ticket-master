package lab.tall15421542.app;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.state.ValueAndTimestamp;


import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.Area;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.ReserveSeat;
import lab.tall15421542.app.avro.reservation.Seat;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.ReservationResultEnum;
import lab.tall15421542.app.avro.reservation.ReservationErrorCodeEnum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static AreaStatus toAreaStatus(String eventName, Area area){
        String areaId = area.getAreaId().toString();
        int rowCount = area.getRowCount(), colCount = area.getColCount();
        int availableSeats = rowCount * colCount;
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < rowCount ; ++i){
            List<SeatStatus> row = new ArrayList<>();
            for(int j = 0 ; j < colCount ; ++j){
                row.add(j, new SeatStatus(i, j, true));
            }
            seats.add(row);
        }
        return new AreaStatus(
                eventName, areaId, area.getPrice(), rowCount, colCount, availableSeats, seats
        );
    }

    private static class ReserveSeatTransformer implements Transformer<String, ReserveSeat, KeyValue<String, ReservationResult>>{
        private KeyValueStore<String, ValueAndTimestamp<AreaStatus>> areaStatusStore;
        @Override
        public void init(ProcessorContext context){
            areaStatusStore = context.getStateStore(Schemas.Stores.AREA_STATUS.name());
        }

        @Override
        public KeyValue<String, ReservationResult> transform(String eventAreaId, ReserveSeat req){
            ValueAndTimestamp<AreaStatus> areaStatusAndTimestamp = areaStatusStore.get(eventAreaId);
            AreaStatus areaStatus = ValueAndTimestamp.getValueOrNull(areaStatusAndTimestamp);
            ReservationResult result = new ReservationResult();
            String reservationId = req.getReservationId().toString();
            result.setReservationId(reservationId);

            if(areaStatus == null){
                result.setResult(ReservationResultEnum.FAILED);
                result.setErrorCode(ReservationErrorCodeEnum.INVALID_EVENT_AREA);
                result.setErrorMessage(String.format("%s event area does not exist", eventAreaId));
                return KeyValue.pair(reservationId, result);
            }

            for(Seat seat: req.getSeats()){
                int row = seat.getRow(), col = seat.getCol();
                int areaRowCount = areaStatus.getRowCount(), areaColCount = areaStatus.getColCount();
                if(row < 0 || row >= areaRowCount || col < 0 || col >= areaColCount){
                    result.setResult(ReservationResultEnum.FAILED);
                    result.setErrorCode(ReservationErrorCodeEnum.INVALID_SEAT);
                    result.setErrorMessage(
                            String.format("%s (%d, %d) is not a valid seat.", eventAreaId, row, col)
                    );
                    return KeyValue.pair(reservationId, result);
                }

                if(areaStatus.getSeats().get(row).get(col).getIsAvailable() == false){
                    result.setResult(ReservationResultEnum.FAILED);
                    result.setErrorCode(ReservationErrorCodeEnum.NOT_AVAILABLE);
                    result.setErrorMessage(
                            String.format("%s (%d, %d) is unavailable.", eventAreaId, row, col, eventAreaId)
                    );
                    return KeyValue.pair(reservationId, result);
                }
            }

            for(Seat seat: req.getSeats()){
                SeatStatus seatStatus = areaStatus.getSeats().get(seat.getRow()).get(seat.getCol());
                seatStatus.setIsAvailable(false);
            }

            areaStatusStore.put(eventAreaId, areaStatusAndTimestamp);
            result.setResult(ReservationResultEnum.SUCCESS);
            result.setSeats(req.getSeats());

            return KeyValue.pair(reservationId, result);
        }

        @Override
        public void close(){
            // do nothing
        }
    }
    public static void main(final String[] args) throws Exception {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();

        // Create Event Flow
        KStream<String, CreateEvent> createEventReqs = builder.stream(Topics.CREATE_EVENT.name(),
                        Consumed.with(Topics.CREATE_EVENT.keySerde(), Topics.CREATE_EVENT.valueSerde()));

        KStream<String, AreaStatus> createEventAreas = createEventReqs.flatMap(
                (eventName, createEvent) -> {
                    List<KeyValue<String, AreaStatus>> areas = new LinkedList<>();
                    for(Area area: createEvent.getAreas()){
                        areas.add(KeyValue.pair(eventName + "#" + area.getAreaId(), toAreaStatus(eventName, area)));
                    }
                    return areas;
                }
        );

        createEventAreas.toTable(
                Materialized.<String, AreaStatus, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.AREA_STATUS.name())
                        .withKeySerde(Schemas.Stores.AREA_STATUS.keySerde())
                        .withValueSerde(Schemas.Stores.AREA_STATUS.valueSerde())
        );

        // Reservation Flow
        KStream<String, ReserveSeat> reserveSeatReqs = builder.stream(Topics.RESERVE_SEAT.name(),
                Consumed.with(Topics.RESERVE_SEAT.keySerde(), Topics.RESERVE_SEAT.valueSerde()));

        KStream<String, ReservationResult> reserveResult = reserveSeatReqs.transform(new TransformerSupplier() {
            public Transformer get() {
                return new ReserveSeatTransformer();
            }
        }, Schemas.Stores.AREA_STATUS.name());

        reserveResult.to(Topics.RESERVATION_RESULT.name(), Produced.with(
                Topics.RESERVATION_RESULT.keySerde(),
                Topics.RESERVATION_RESULT.valueSerde()
        ));

        final Topology topology = builder.build();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "event-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                MyDeserializationExceptionHandler.class.getName());

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        streams.close();
    }

    public static class MyDeserializationExceptionHandler implements DeserializationExceptionHandler {
        @Override
        public DeserializationHandlerResponse handle(ProcessorContext context,  ConsumerRecord<byte[],byte[]> record, Exception e) {
            // Log the error and continue processing
            System.err.println("Error deserializing record: " + e.getMessage());
            return DeserializationHandlerResponse.CONTINUE;  // Continue processing other records
        }

        @Override
        public void configure(Map<String, ?> configs) {
            // You can access configuration properties here if needed
            System.out.println("Configuring MyDeserializationExceptionHandler with configs: " + configs);
        }
    }

}
