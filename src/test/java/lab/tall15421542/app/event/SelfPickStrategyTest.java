package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelfPickStrategyTest {
    @Test
    void successReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 2 ; ++i){
            seats.add(new ArrayList<>());
            for(int j = 0 ; j < 2 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus(
                "event", "A", 100, 2, 2, 4, seats
        );

        List<Seat> requestedSeats = new ArrayList<>();
        requestedSeats.add(new Seat(0, 0));
        requestedSeats.add(new Seat(0, 1));
        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", 2, 2, ReservationTypeEnum.SELF_PICK, requestedSeats
        );

        Service.ReservationStrategy strategy = new SelfPickStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.SUCCESS);
        expectedResult.setSeats(requestedSeats);

        assertEquals(expectedResult, strategy.reserve(areaStatus, req));
    }

    @Test
    void notAvailableReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 2 ; ++i){
            seats.add(new ArrayList<>());
            for(int j = 0 ; j < 2 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, false));
            }
        }
        AreaStatus areaStatus = new AreaStatus(
                "event", "A", 100, 2, 2, 4, seats
        );

        List<Seat> requestedSeats = new ArrayList<>();
        requestedSeats.add(new Seat(0, 0));
        requestedSeats.add(new Seat(0, 1));
        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", 2, 2, ReservationTypeEnum.SELF_PICK, requestedSeats
        );

        Service.ReservationStrategy strategy = new SelfPickStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.FAILED);
        expectedResult.setErrorCode(ReservationErrorCodeEnum.NOT_AVAILABLE);
        expectedResult.setErrorMessage("event#A (0, 0) is unavailable.");

        assertEquals(expectedResult, strategy.reserve(areaStatus, req));
    }

    @Test
    void invalidAvailableReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 2 ; ++i){
            seats.add(new ArrayList<>());
            for(int j = 0 ; j < 2 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus(
                "event", "A", 100, 2, 2, 4, seats
        );

        List<Seat> requestedSeats = new ArrayList<>();
        requestedSeats.add(new Seat(0, 1));
        requestedSeats.add(new Seat(0, 2));
        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", 2, 2, ReservationTypeEnum.SELF_PICK, requestedSeats
        );

        Service.ReservationStrategy strategy = new SelfPickStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.FAILED);
        expectedResult.setErrorCode(ReservationErrorCodeEnum.INVALID_SEAT);
        expectedResult.setErrorMessage("event#A (0, 2) is not a valid seat.");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        requestedSeats.clear();
        requestedSeats.add(new Seat(0, -1));
        req.setSeats(requestedSeats);
        expectedResult.setErrorMessage("event#A (0, -1) is not a valid seat.");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        requestedSeats.clear();
        requestedSeats.add(new Seat(-1, 0));
        req.setSeats(requestedSeats);
        expectedResult.setErrorMessage("event#A (-1, 0) is not a valid seat.");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        requestedSeats.clear();
        requestedSeats.add(new Seat(2, 0));
        req.setSeats(requestedSeats);
        expectedResult.setErrorMessage("event#A (2, 0) is not a valid seat.");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));
    }
}