package lab.tall15421542.app;

import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReserveSeat;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import lab.tall15421542.app.avro.reservation.Seat;
import lab.tall15421542.app.avro.reservation.ReservationResultEnum;
import lab.tall15421542.app.avro.reservation.StateEnum;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PipedReader;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MaxLRUEntries = 1000;

    private static class ReservationResultProcessor implements FixedKeyProcessor<String, ReservationResult, Void>{
        private KeyValueStore<String, ValueAndTimestamp<Reservation>> reservationStore;

        @Override
        public void init(FixedKeyProcessorContext context){
            reservationStore = context.getStateStore(Schemas.Stores.RESERVATION.name());
        }

        @Override
        public void process(FixedKeyRecord<String, ReservationResult> record){
            String reservationId = record.key();
            ReservationResult reservationResult = record.value();

            ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get(reservationId);
            Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
            if(reservation == null){
                System.out.println("reservation id: " + reservationId + " does not exist.");
                return;
            }

            if (reservationResult.getResult() == ReservationResultEnum.SUCCESS) {
                reservation.setState(StateEnum.RESERVED);
                reservation.setSeats(reservationResult.getSeats());
            } else if (reservationResult.getResult() == ReservationResultEnum.FAILED) {
                reservation.setState(StateEnum.FAILED);
                reservation.setFailedReason(
                        String.format("[%s]: %s", reservationResult.getErrorCode(), reservationResult.getErrorMessage())
                );
            } else {
                reservation.setState(StateEnum.FAILED);
                reservation.setFailedReason(String.format("Invalid result: %s", reservationResult.getResult()));
            }

            reservationStore.put(reservationId, ValueAndTimestamp.make(reservation, Instant.now().toEpochMilli()));
        }

        @Override
        public void close(){

        }
    }
    private static interface FilterStrategy {
        boolean pass(AreaStatus areaStatus, ReserveSeat req);
    }

    private static class SelfPickFilterStrategy implements FilterStrategy{
        @Override
        public boolean pass(AreaStatus areaStatus, ReserveSeat req){
            int rowCount = areaStatus.getRowCount(), colCount = areaStatus.getColCount();
            for(Seat seat: req.getSeats()){
                int r = seat.getRow(), c = seat.getCol();
                if(r < 0 || r >= rowCount || c < 0 || c >= colCount){
                    return false;
                }
                if(areaStatus.getSeats().get(r).get(c).getIsAvailable() == false){
                    return false;
                }
            }
            return true;
        }
    }

    private static class RandomContinuousFilterStrategy implements FilterStrategy {
        @Override
        public boolean pass(AreaStatus areaStatus, ReserveSeat req){
            int colCount = areaStatus.getColCount();
            if(req.getNumOfSeats() > areaStatus.getAvailableSeats() || req.getNumOfSeats() > colCount){
                return false;
            }
            return true;
        }
    }

    private static class ReservationTransformer implements ValueTransformer<ReserveSeat, Reservation>{
        private KeyValueStore<String, ValueAndTimestamp<AreaStatus>> eventAreaStatusCache;
        private Map<ReservationTypeEnum, FilterStrategy> filterStrategies;

        @Override
        public void init(ProcessorContext context){
            eventAreaStatusCache = context.getStateStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name());
            filterStrategies = new HashMap<>();
            filterStrategies.put(ReservationTypeEnum.SELF_PICK, new SelfPickFilterStrategy());
            filterStrategies.put(ReservationTypeEnum.RANDOM, new RandomContinuousFilterStrategy());
        }

        @Override
        public Reservation transform(ReserveSeat req){
            Reservation reservation = new Reservation(
                    req.getReservationId(),
                    req.getEventId(),
                    req.getAreaId(),
                    req.getNumOfSeats(),
                    req.getNumOfSeat(),
                    req.getType(),
                    req.getSeats(),
                    StateEnum.PROCESSING,
                    ""
            );

            String eventAreaId = req.getEventId() + "#" + req.getAreaId();
            ValueAndTimestamp<AreaStatus> areaStatusAndTimestamp = eventAreaStatusCache.get(eventAreaId);
            AreaStatus areaStatus = ValueAndTimestamp.getValueOrNull(areaStatusAndTimestamp);

            // eventAreaId is not in the cache, forward to event service;
            if(areaStatus == null){
                return reservation;
            }

            FilterStrategy filter = filterStrategies.get(req.getType());
            if(filter == null){
                reservation.setState(StateEnum.FAILED);
                reservation.setFailedReason(String.format("%s type reservation is not supported", req.getType().toString()));
                return reservation;
            }

            if(filter.pass(areaStatus, req)){
                return reservation;
            }

            reservation.setState(StateEnum.FAILED);
            reservation.setFailedReason(String.format("request rejected at cache level"));
            return reservation;
        }

        @Override
        public void close(){
            this.filterStrategies = null;
        }
    }

    public static void main(final String[] args) throws Exception {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();

        builder.globalTable(
                Topics.EVENT_AREA_STATUS_UPDATE.name(),
                Materialized.<String, AreaStatus>as(
                            Stores.lruMap(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name(), MaxLRUEntries)
                        )
                        .withKeySerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.keySerde())
                        .withValueSerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.valueSerde())
        );

        KStream<String, ReserveSeat> reservationRequests = builder.stream(
                Topics.RESERVATION_RESERVE_SEAT.name(),
                Consumed.with(
                    Topics.RESERVATION_RESERVE_SEAT.keySerde(),
                    Topics.RESERVATION_RESERVE_SEAT.valueSerde()
                )
        );

        KStream<String, Reservation> createReservationStream = reservationRequests.transformValues(
                ()-> new ReservationTransformer());

        KTable<String, Reservation> reservationTable = createReservationStream.toTable(
                Materialized.<String, Reservation, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.RESERVATION.name())
                        .withKeySerde(Schemas.Stores.RESERVATION.keySerde())
                        .withValueSerde(Schemas.Stores.RESERVATION.valueSerde())
                        .withCachingDisabled()
        );

        KStream<String, Reservation> reservationStatusUpdatedStream = reservationTable.toStream();

        final String PROCESSING = "processing", FAILED = "failed", PREFIX = "reservation";
        Map<String, KStream<String, Reservation>> result = reservationStatusUpdatedStream.split(Named.as(PREFIX))
                .branch(
                        (reservationId, reservation) -> reservation.getState() == StateEnum.FAILED,
                        Branched.as(FAILED)
                )
                .defaultBranch(Branched.as(PROCESSING));

        System.out.println(result.keySet());
        KStream<String, Reservation> rejectedReservation = result.get(PREFIX + FAILED);
        KStream<String, Reservation> processingReservation = result.get(PREFIX + PROCESSING);

        rejectedReservation.foreach((reservationId, reservation) -> System.out.println(reservationId + ":" + reservation));
        KStream<String, ReserveSeat> processingReqs = processingReservation.map(
                (reservationId, reservation) -> KeyValue.pair(
                        reservation.getEventId().toString() + "#" + reservation.getAreaId(),
                        new ReserveSeat(
                                reservation.getReservationId(),
                                reservation.getEventId(),
                                reservation.getAreaId(),
                                reservation.getNumOfSeats(),
                                reservation.getNumOfSeat(),
                                reservation.getType(),
                                reservation.getSeats()
                        )
                )
        );

        processingReqs.to(Topics.RESERVE_SEAT.name(), Produced.with(Topics.RESERVE_SEAT.keySerde(), Topics.RESERVE_SEAT.valueSerde()));

        // TODO: consume reservationResult, trigger reservation state change in state store
        KStream<String, ReservationResult> reservationResults = builder.stream(
                Topics.RESERVATION_RESULT.name(),
                Consumed.with(
                        Topics.RESERVATION_RESULT.keySerde(),
                        Topics.RESERVATION_RESULT.valueSerde()
                )
        );

        reservationResults.processValues(()-> new ReservationResultProcessor(), Schemas.Stores.RESERVATION.name());

        final Topology topology = builder.build();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "reservation-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        streams.close();
    }
}
