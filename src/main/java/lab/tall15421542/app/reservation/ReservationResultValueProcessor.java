package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.time.Instant;

class ReservationResultValueProcessor implements FixedKeyProcessor<String, ReservationResult, Reservation> {
    private KeyValueStore<String, ValueAndTimestamp<Reservation>> reservationStore;
    private FixedKeyProcessorContext<String, Reservation> context;

    @Override
    public void init(FixedKeyProcessorContext<String, Reservation> context) {
        this.context = context;
        reservationStore = context.getStateStore(Schemas.Stores.RESERVATION.name());
    }

    @Override
    public void process(FixedKeyRecord<String, ReservationResult> record) {
        ReservationResult reservationResult = record.value();
        String reservationId = record.key();

        ValueAndTimestamp<Reservation> reservationAndTimestamp = reservationStore.get(reservationId);
        Reservation reservation = ValueAndTimestamp.getValueOrNull(reservationAndTimestamp);
        if (reservation == null) {
            System.out.println("reservation id: " + reservationId + " does not exist.");
            context.forward(record.withValue(reservation));
            return;
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
        context.forward(record.withValue(reservation));
    }

    @Override
    public void close() {

    }
}
