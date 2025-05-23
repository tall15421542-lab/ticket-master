package lab.tall15421542.app.domain.beans;

import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.Seat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ReservationBean {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationBean that = (ReservationBean) o;
        return getNumOfSeats() == that.getNumOfSeats() && getNumOfSeat() == that.getNumOfSeat() && Objects.equals(getReservationId(), that.getReservationId()) && Objects.equals(getUserId(), that.getUserId()) && Objects.equals(getEventId(), that.getEventId()) && Objects.equals(getAreaId(), that.getAreaId()) && Objects.equals(getType(), that.getType()) && Objects.equals(getSeats(), that.getSeats()) && Objects.equals(getState(), that.getState()) && Objects.equals(getFailedReason(), that.getFailedReason());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getReservationId(), getUserId(), getEventId(), getAreaId(), getNumOfSeats(), getNumOfSeat(), getType(), getSeats(), getState(), getFailedReason());
    }

    public static class SeatBean{
        private int row;
        private int col;

        public SeatBean(){}
        public SeatBean(int row, int col){
            this.row = row;
            this.col = col;
        }

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SeatBean seatBean = (SeatBean) o;
            return getRow() == seatBean.getRow() && getCol() == seatBean.getCol();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getRow(), getCol());
        }
    };

    private String reservationId;
    private String userId;
    private String eventId;
    private String areaId;
    private int numOfSeats = 0;
    private int numOfSeat = 0;
    private String type;
    private List<SeatBean> seats = new ArrayList<>();
    private String state;
    private String failedReason = "";

    public ReservationBean(){}
    public ReservationBean(
            String reservationId, String userId, String eventId, String areaId,
            int numOfSeats, int numOfSeat, String type, List<SeatBean> seats,  String state,
            String failedReason){
        this.reservationId = reservationId;
        this.userId = userId;
        this.eventId = eventId;
        this.areaId = areaId;
        this.numOfSeats = numOfSeats;
        this.numOfSeat = numOfSeat;
        this.type = type;
        this.seats = seats;
        this.state = state;
        this.failedReason = failedReason;
    }

    // Getters and Setters

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

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

    public void setNumOfSeats(int numOfSeats) {
        this.numOfSeats = numOfSeats;
    }

    public int getNumOfSeat() {
        return numOfSeat;
    }

    public void setNumOfSeat(int numOfSeat) {
        this.numOfSeat = numOfSeat;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<SeatBean> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatBean> seats) {
        this.seats = seats;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason = failedReason;
    }

    public static ReservationBean fromAvro(Reservation reservation){
        List<SeatBean> seats = new ArrayList<>();
        for(Seat seat: reservation.getSeats()){
            seats.add(new SeatBean(seat.getRow(), seat.getCol()));
        }
        return new ReservationBean(
                reservation.getReservationId().toString(),
                reservation.getUserId().toString(),
                reservation.getEventId().toString(),
                reservation.getAreaId().toString(),
                reservation.getNumOfSeats(),
                reservation.getNumOfSeat(),
                reservation.getType().toString(),
                seats,
                reservation.getState().toString(),
                reservation.getFailedReason().toString()
        );
    }
}
