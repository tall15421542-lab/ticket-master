package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.ReservationErrorCodeEnum;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.ReservationResultEnum;
import lab.tall15421542.app.avro.reservation.Seat;

import java.util.ArrayList;
import java.util.List;

class ContinuousRandomStrategy implements Service.ReservationStrategy {
    public ReservationResult reserve(AreaStatus areaStatus, ReserveSeat req) {
        ReservationResult result = new ReservationResult();
        String reservationId = req.getReservationId().toString();
        result.setReservationId(reservationId);

        if (req.getNumOfSeats() <= 0) {
            result.setResult(ReservationResultEnum.FAILED);
            result.setErrorCode(ReservationErrorCodeEnum.INVALID_ARGUMENT);
            result.setErrorMessage(String.format("%d continuous seats is invalid", req.getNumOfSeats()));
            return result;
        }

        int rowCount = areaStatus.getRowCount(), colCount = areaStatus.getColCount();
        for (int r = 0; r < rowCount; ++r) {
            List<SeatStatus> rowStatus = areaStatus.getSeats().get(r);
            int left = 0;
            while (req.getNumOfSeats() <= colCount - left) {
                if (!rowStatus.get(left).getIsAvailable()) {
                    ++left;
                    continue;
                }

                int right = left + 1;
                for (; right < left + req.getNumOfSeats(); ++right) {
                    if (!rowStatus.get(right).getIsAvailable()) {
                        left = right + 1;
                        break;
                    }
                }

                if (right - left == req.getNumOfSeats()) {
                    List<Seat> seats = new ArrayList<>();
                    for (int c = left; c < right; ++c) {
                        Seat seat = new Seat();
                        seat.setRow(r);
                        seat.setCol(c);
                        seats.add(seat);
                    }

                    result.setResult(ReservationResultEnum.SUCCESS);
                    result.setSeats(seats);
                    return result;
                }
            }
        }

        result.setResult(ReservationResultEnum.FAILED);
        result.setErrorCode(ReservationErrorCodeEnum.NOT_AVAILABLE);
        result.setErrorMessage(String.format("no continous %d seats at area %s in event %s",
                req.getNumOfSeats(), req.getAreaId(), req.getEventId())
        );
        return result;
    }
}
