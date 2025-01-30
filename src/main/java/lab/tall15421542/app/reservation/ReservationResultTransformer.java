package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.ReservationResultEnum;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.time.Instant;

class ReservationResultTransformer implements ValueTransformer<ReservationResult, Reservation> {
    private KeyValueStore<String, ValueAndTimestamp<Reservation>> reservationStore;

    @Override
    public void init(ProcessorContext context) {
        reservationStore = context.getStateStore(Schemas.Stores.RESERVATION.name());
    }

    @Override
    public Reservation transform(ReservationResult reservationResult) {
        String reservationId = reservationResult.getReservationId().toString();

        ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get(reservationId);
        Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
        if (reservation == null) {
            System.out.println("reservation id: " + reservationId + " does not exist.");
            return reservation;
        }

        switch(reservationResult.getResult()){
            case SUCCESS: {
                reservation.setState(StateEnum.RESERVED);
                reservation.setSeats(reservationResult.getSeats());
                break;
            }
            case FAILED: {
                reservation.setState(StateEnum.FAILED);
                reservation.setFailedReason(
                        String.format("[%s]: %s", reservationResult.getErrorCode(), reservationResult.getErrorMessage())
                );
                break;
            }
            default:
        }

        reservationStore.put(reservationId, ValueAndTimestamp.make(reservation, Instant.now().toEpochMilli()));
        return reservation;
    }

    @Override
    public void close() {

    }
}
