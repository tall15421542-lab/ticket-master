package lab.tall15421542.app.domain.beans;

import lab.tall15421542.app.avro.reservation.ReserveSeat;
import lab.tall15421542.app.avro.reservation.Seat;

import java.util.List;
import java.util.ArrayList;

public class ReservationBean {
    private String reservationId;
    private String eventId;
    private String areaId;
    private int numOfSeat;
    private List<SeatBean> seats;

    // Getters and Setters

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAreaId() {
        return areaId;
    }

    public void setAreaId(String areaId) {
        this.areaId = areaId;
    }

    public int getNumOfSeat() {
        return numOfSeat;
    }

    public void setNumOfSeat(int numOfSeat) {
        this.numOfSeat = numOfSeat;
    }

    public List<SeatBean> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatBean> seats) {
        this.seats = seats;
    }

    // Inner Seat class for the seats array
    public static class SeatBean {

        private int row;
        private int col;

        // Getters and Setters

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public int getCol() {
            return col;
        }

        public void setCol(int col) {
            this.col = col;
        }
    }

    public ReserveSeat toAvro(){
        ReserveSeat avroReserveSeat = new ReserveSeat();
        avroReserveSeat.setReservationId(this.reservationId);
        avroReserveSeat.setEventId(this.eventId);
        avroReserveSeat.setAreaId(this.areaId);
        avroReserveSeat.setNumOfSeat(this.numOfSeat);

        // Convert the list of Seat objects to an Avro array
        List<Seat> avroSeats = new ArrayList<>();
        if (this.seats != null) {
            for (SeatBean seat : this.seats) {
                Seat avroSeat = new Seat();
                avroSeat.setRow(seat.getRow());
                avroSeat.setCol(seat.getCol());
                avroSeats.add(avroSeat);
            }
        }
        avroReserveSeat.put("seats", avroSeats);

        return avroReserveSeat;
    }
}

