package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;
import lab.tall15421542.app.avro.reservation.Seat;
import lab.tall15421542.app.reservation.ReservationValueProcessor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelfPickFilterStrategyTest {
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
                "userId", "event", "A", 3, 3, ReservationTypeEnum.SELF_PICK,
                Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2))
        );

        ReservationValueProcessor.FilterStrategy strategy = new SelfPickFilterStrategy();
        assertTrue(strategy.pass(areaStatus, req));
    }

    @Test
    void rejectNotAvailableReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        seats.get(0).get(1).setIsAvailable(false);
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 3, 3, ReservationTypeEnum.SELF_PICK,
                Arrays.asList(new Seat(0,0), new Seat(0,1), new Seat(0,2))
        );

        ReservationValueProcessor.FilterStrategy strategy = new SelfPickFilterStrategy();
        assertFalse(strategy.pass(areaStatus, req));
    }

    @Test
    void rejectInvalidReservation(){
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < 3 ; ++i){
            seats.add(new ArrayList<SeatStatus>());
            for(int j = 0 ; j < 3 ; ++j){
                seats.get(i).add(new SeatStatus(i, j, true));
            }
        }
        seats.get(0).get(1).setIsAvailable(false);
        AreaStatus areaStatus = new AreaStatus("event", "A", 100, 3, 3, 9, seats);
        CreateReservation req = new CreateReservation(
                "userId", "event", "A", 1, 1, ReservationTypeEnum.SELF_PICK,
                Arrays.asList(new Seat(0,4))
        );

        ReservationValueProcessor.FilterStrategy strategy = new SelfPickFilterStrategy();
        assertFalse(strategy.pass(areaStatus, req));

        req.setSeats(Arrays.asList(new Seat(0, -1)));
        assertFalse(strategy.pass(areaStatus, req));

        req.setSeats(Arrays.asList(new Seat(4, 0)));
        assertFalse(strategy.pass(areaStatus, req));

        req.setSeats(Arrays.asList(new Seat(-1, 0)));
        assertFalse(strategy.pass(areaStatus, req));
    }
}