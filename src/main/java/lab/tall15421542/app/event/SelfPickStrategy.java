package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.reservation.ReservationErrorCodeEnum;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.ReservationResultEnum;
import lab.tall15421542.app.avro.reservation.Seat;

class SelfPickStrategy implements Service.ReservationStrategy {
    @Override
    public ReservationResult reserve(AreaStatus areaStatus, ReserveSeat req) {
        ReservationResult result = new ReservationResult();
        String eventAreaId = req.getEventId().toString() + "#" + req.getAreaId().toString();
        String reservationId = req.getReservationId().toString();
        result.setReservationId(reservationId);

        int areaRowCount = areaStatus.getRowCount(), areaColCount = areaStatus.getColCount();
        for (Seat seat : req.getSeats()) {
            int row = seat.getRow(), col = seat.getCol();
            if (row < 0 || row >= areaRowCount || col < 0 || col >= areaColCount) {
                result.setResult(ReservationResultEnum.FAILED);
                result.setErrorCode(ReservationErrorCodeEnum.INVALID_SEAT);
                result.setErrorMessage(
                        String.format("%s (%d, %d) is not a valid seat.", eventAreaId, row, col)
                );
                return result;
            }

            if (areaStatus.getSeats().get(row).get(col).getIsAvailable() == false) {
                result.setResult(ReservationResultEnum.FAILED);
                result.setErrorCode(ReservationErrorCodeEnum.NOT_AVAILABLE);
                result.setErrorMessage(
                        String.format("%s (%d, %d) is unavailable.", eventAreaId, row, col, eventAreaId)
                );
                return result;
            }
        }

        result.setResult(ReservationResultEnum.SUCCESS);
        result.setSeats(req.getSeats());

        return result;
    }
}
