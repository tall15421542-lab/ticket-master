package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Seat;
import lab.tall15421542.app.reservation.ReservationTransformer;

public class SelfPickFilterStrategy implements ReservationTransformer.FilterStrategy {
    @Override
    public boolean pass(AreaStatus areaStatus, CreateReservation req) {
        int rowCount = areaStatus.getRowCount(), colCount = areaStatus.getColCount();
        for (Seat seat : req.getSeats()) {
            int r = seat.getRow(), c = seat.getCol();
            if (r < 0 || r >= rowCount || c < 0 || c >= colCount) {
                return false;
            }
            if (areaStatus.getSeats().get(r).get(c).getIsAvailable() == false) {
                return false;
            }
        }
        return true;
    }
}
