package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.*;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class ReserveSeatTransformer implements Transformer<String, ReserveSeat, KeyValue<String, ReservationResult>> {
    private KeyValueStore<String, ValueAndTimestamp<AreaStatus>> areaStatusStore;
    private Map<ReservationTypeEnum, Service.ReservationStrategy> reservationStrategies;

    @Override
    public void init(ProcessorContext context) {
        areaStatusStore = context.getStateStore(Schemas.Stores.AREA_STATUS.name());
        reservationStrategies = new HashMap<>();
        reservationStrategies.put(ReservationTypeEnum.SELF_PICK, new SelfPickStrategy());
        reservationStrategies.put(ReservationTypeEnum.RANDOM, new ContinuousRandomStrategy());
    }

    @Override
    public KeyValue<String, ReservationResult> transform(String eventAreaId, ReserveSeat req) {
        ValueAndTimestamp<AreaStatus> areaStatusAndTimestamp = areaStatusStore.get(eventAreaId);
        AreaStatus areaStatus = ValueAndTimestamp.getValueOrNull(areaStatusAndTimestamp);
        String reservationId = req.getReservationId().toString();

        if (areaStatus == null) {
            ReservationResult result = new ReservationResult();
            result.setReservationId(reservationId);
            result.setResult(ReservationResultEnum.FAILED);
            result.setErrorCode(ReservationErrorCodeEnum.INVALID_EVENT_AREA);
            result.setErrorMessage(String.format("%s event area does not exist", eventAreaId));
            return KeyValue.pair(reservationId, result);
        }

        Service.ReservationStrategy reservationStrategy = reservationStrategies.get(req.getType());
        if (reservationStrategy == null) {
            ReservationResult result = new ReservationResult();
            result.setReservationId(reservationId);
            result.setResult(ReservationResultEnum.FAILED);
            result.setErrorCode(ReservationErrorCodeEnum.INVALID_ARGUMENT);
            result.setErrorMessage(String.format("%s reservation strategy is not implemented", req.getType()));
            return KeyValue.pair(reservationId, result);
        }

        ReservationResult result = reservationStrategy.reserve(areaStatus, req);

        if (result.getResult() == ReservationResultEnum.SUCCESS) {
            for (Seat seat : result.getSeats()) {
                SeatStatus seatStatus = areaStatus.getSeats().get(seat.getRow()).get(seat.getCol());
                seatStatus.setIsAvailable(false);
            }
            int availableSeats = areaStatus.getAvailableSeats() - result.getSeats().size();
            areaStatus.setAvailableSeats(availableSeats);
            areaStatusStore.put(eventAreaId, ValueAndTimestamp.make(areaStatus, Instant.now().toEpochMilli()));
        }

        return KeyValue.pair(reservationId, result);
    }

    @Override
    public void close() {
        // do nothing
    }
}
