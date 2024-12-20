package lab.tall15421542.app.domain.beans;

import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Seat;
import lab.tall15421542.app.avro.reservation.ReservationTypeEnum;

import java.util.List;
import java.util.ArrayList;

public class ReservationBean {
    private String userId;
    private String eventId;
    private String areaId;
    private int numOfSeats;
    private List<SeatBean> seats;
    private String type;

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public int getNumOfSeats() {
        return numOfSeats;
    }

    public void setNumOfSeats(int numOfSeat) {
        this.numOfSeats = numOfSeat;
    }

    public List<SeatBean> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatBean> seats) {
        this.seats = seats;
    }

    public String getType() {
        return type;
    }

    public void setType (String type){
        this.type = type;
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

    public CreateReservation toAvro(){
        CreateReservation avroCreateReservation = new CreateReservation();
        avroCreateReservation.setUserId(this.userId);
        avroCreateReservation.setEventId(this.eventId);
        avroCreateReservation.setAreaId(this.areaId);
        avroCreateReservation.setNumOfSeats(this.numOfSeats);

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
        avroCreateReservation.put("seats", avroSeats);

        try{
            avroCreateReservation.put("type", ReservationTypeEnum.valueOf(this.type));
        }catch(IllegalArgumentException | NullPointerException e){
            avroCreateReservation.put("type", ReservationTypeEnum.INVALID);
        }

        return avroCreateReservation;
    }
}

