package lab.tall15421542.app.ticket;

import lab.tall15421542.app.avro.reservation.Reservation;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

class ReservationTransformer implements Transformer<String, Reservation, KeyValue<String, Reservation>> {
    private ProcessorContext context;

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
    }

    @Override
    public KeyValue<String, Reservation> transform(String reservationId, Reservation reservation) {
        String requestId = new String(this.context.headers().lastHeader("request-id").value());
        return KeyValue.pair(requestId, reservation);
    }

    @Override
    public void close() {

    }
}
