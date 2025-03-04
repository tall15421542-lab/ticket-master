package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import lab.tall15421542.app.reservation.ReservationValueProcessor;

class ContinuousRandomFilterStrategyTest {
    @Test
    void acceptedReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        ReservationValueProcessor.FilterStrategy strategy = new ContinuousRandomFilterStrategy();
        assertTrue(strategy.pass(areaStatus, req));
    }

    @Test
    void passRejectedReservationToEventService(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        seats.get(0).get(1).setIsAvailable(false);
        seats.get(1).get(1).setIsAvailable(false);
        seats.get(2).get(1).setIsAvailable(false);
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 6, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        ReservationValueProcessor.FilterStrategy strategy = new ContinuousRandomFilterStrategy();
        assertTrue(strategy.pass(areaStatus, req));
    }

    @Test
    void rejectExceedingRowCapacityReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, false));
            }
        }

        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 0, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 1, 1, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        ReservationValueProcessor.FilterStrategy strategy = new ContinuousRandomFilterStrategy();
        assertFalse(strategy.pass(areaStatus, req));
    }

    @Test
    void rejectExceedingAreaCapacityReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }

        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 4, 4, ReservationTypeEnum.RANDOM, new ArrayList<>()
        );

        ReservationValueProcessor.FilterStrategy strategy = new ContinuousRandomFilterStrategy();
        assertFalse(strategy.pass(areaStatus, req));
    }
}