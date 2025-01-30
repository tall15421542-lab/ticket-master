package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.event.RandomContinuousFilterStrategy;
import lab.tall15421542.app.event.SelfPickFilterStrategy;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReservationTransformer implements Transformer<String, CreateReservation, KeyValue<String, Reservation>> {
    private KeyValueStore<String, ValueAndTimestamp<AreaStatus>> eventAreaStatusCache;
    private Map<ReservationTypeEnum, FilterStrategy> filterStrategies;

    public static interface FilterStrategy {
        boolean pass(AreaStatus areaStatus, CreateReservation req);
    }

    @Override
    public void init(ProcessorContext context) {
        eventAreaStatusCache = context.getStateStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name());
        filterStrategies = new HashMap<>();
        filterStrategies.put(ReservationTypeEnum.SELF_PICK, new SelfPickFilterStrategy());
        filterStrategies.put(ReservationTypeEnum.RANDOM, new RandomContinuousFilterStrategy());
    }

    @Override
    public KeyValue<String, Reservation> transform(String userId, CreateReservation req) {
        String reservationId = UUID.randomUUID().toString();
        Reservation reservation = new Reservation(
                reservationId,
                userId,
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
        if (areaStatus == null) {
            return KeyValue.pair(reservationId, reservation);
        }

        FilterStrategy filter = filterStrategies.get(req.getType());
        if (filter == null) {
            reservation.setState(StateEnum.FAILED);
            reservation.setFailedReason(String.format("%s type reservation is not supported", req.getType().toString()));
            return KeyValue.pair(reservationId, reservation);
        }

        if (filter.pass(areaStatus, req)) {
            return KeyValue.pair(reservationId, reservation);
        }

        reservation.setState(StateEnum.FAILED);
        reservation.setFailedReason(String.format("request rejected at cache level"));
        return KeyValue.pair(reservationId, reservation);
    }

    @Override
    public void close() {
        this.filterStrategies = null;
    }
}
