package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.*;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContinuousRandomStrategyTest {
    @Test
    void successReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus(
                "event", "A", 100, 3, 3, 9, seats
        );

        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", 2, 2, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        Service.ReservationStrategy strategy = new ContinuousRandomStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.SUCCESS);
        List<Seat> expectedSeats = new ArrayList<>();
        expectedSeats.add(new Seat(0, 0));
        expectedSeats.add(new Seat(0, 1));
        expectedResult.setSeats(expectedSeats);

        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        areaStatus.getSeats().get(0).get(0).setIsAvailable(false);
        areaStatus.getSeats().get(0).get(1).setIsAvailable(false);
        areaStatus.getSeats().get(1).get(1).setIsAvailable(false);
        areaStatus.getSeats().get(2).get(0).setIsAvailable(false);
        areaStatus.setAvailableSeats(5);
        expectedSeats.clear();
        expectedSeats.add(new Seat(2,1));
        expectedSeats.add(new Seat(2,2));
        expectedResult.setSeats(expectedSeats);
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));
    }

    @Test
    void notAvailableReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        seats.get(0).get(1).setIsAvailable(false);
        seats.get(1).get(1).setIsAvailable(false);
        seats.get(2).get(1).setIsAvailable(false);
        AreaStatus areaStatus = new AreaStatus(
                "event", "A", 100, 3, 3, 6, seats
        );

        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", 2, 2, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        Service.ReservationStrategy strategy = new ContinuousRandomStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.FAILED);
        expectedResult.setErrorCode(ReservationErrorCodeEnum.NOT_AVAILABLE);
        expectedResult.setErrorMessage("no continuous 2 seats at area A in event event");

        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        req.setNumOfSeats(4);
        expectedResult.setErrorMessage("no continuous 4 seats at area A in event event");
    }

    @Test
    void invalidReservation(){
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

        ReserveSeat req = new ReserveSeat(
                "reservationId", "event", "A", -1, -1, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        Service.ReservationStrategy strategy = new ContinuousRandomStrategy();

        ReservationResult expectedResult = new ReservationResult();
        expectedResult.setReservationId("reservationId");
        expectedResult.setResult(ReservationResultEnum.FAILED);
        expectedResult.setErrorCode(ReservationErrorCodeEnum.INVALID_ARGUMENT);
        expectedResult.setErrorMessage("-1 continuous seats is invalid");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));

        req.setNumOfSeats(0);
        expectedResult.setErrorMessage("0 continuous seats is invalid");
        assertEquals(expectedResult, strategy.reserve(areaStatus, req));
    }
}