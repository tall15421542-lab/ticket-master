package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.reservation.ReservationTransformer;

public class ContinuousRandomFilterStrategy implements ReservationTransformer.FilterStrategy {
    @Override
    public boolean pass(AreaStatus areaStatus, CreateReservation req) {
        int colCount = areaStatus.getColCount();
        if (req.getNumOfSeats() > areaStatus.getAvailableSeats() || req.getNumOfSeats() > colCount) {
            return false;
        }
        return true;
    }
}
