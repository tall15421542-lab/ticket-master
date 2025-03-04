package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.event.ContinuousRandomFilterStrategy;
import lab.tall15421542.app.event.SelfPickFilterStrategy;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.util.HashMap;
import java.util.Map;

public class ReservationValueProcessor implements FixedKeyProcessor<String, CreateReservation, Reservation> {
    private KeyValueStore<String, ValueAndTimestamp<AreaStatus>> eventAreaStatusCache;
    private Map<ReservationTypeEnum, FilterStrategy> filterStrategies;
    private FixedKeyProcessorContext<String, Reservation> context;

    public static interface FilterStrategy {
        boolean pass(AreaStatus areaStatus, CreateReservation req);
    }

    @Override
    public void init(FixedKeyProcessorContext<String, Reservation> context) {
        this.context = context;
        eventAreaStatusCache = context.getStateStore(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name());
        filterStrategies = new HashMap<>();
        filterStrategies.put(ReservationTypeEnum.SELF_PICK, new SelfPickFilterStrategy());
        filterStrategies.put(ReservationTypeEnum.RANDOM, new ContinuousRandomFilterStrategy());
    }

    @Override
    public void process(FixedKeyRecord<String, CreateReservation> record) {
        String reservationId = record.key();
        CreateReservation req = record.value();
        Reservation reservation = new Reservation(
                reservationId,
                req.getUserId(),
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
            this.context.forward(record.withValue(reservation));
            return;
        }

        FilterStrategy filter = filterStrategies.get(req.getType());
        if (filter == null) {
            reservation.setState(StateEnum.FAILED);
            reservation.setFailedReason(String.format("%s type reservation is not supported", req.getType().toString()));
            this.context.forward(record.withValue(reservation));
            return;
        }

        if (filter.pass(areaStatus, req)) {
            this.context.forward(record.withValue(reservation));
            return;
        }

        reservation.setState(StateEnum.FAILED);
        reservation.setFailedReason(String.format("request rejected at cache level"));
        this.context.forward(record.withValue(reservation));
    }

    @Override
    public void close() {
        this.filterStrategies = null;
    }
}
