package lab.tall15421542.app.domain.beans;

import lab.tall15421542.lab.app.avro.event.CreateEvent;
import lab.tall15421542.lab.app.avro.event.Area;
import java.util.ArrayList;
import java.time.Instant;

public class EventBean {
    private String eventName;
    private String artist;
    private ArrayList<AreaBean> areaBeans;
    private Instant reservationOpeningTime;
    private Instant reservationClosingTime;
    private Instant eventStartTime;
    private Instant eventEndTime;

    public EventBean(){
        eventName = new String();
        artist = new String();
        areaBeans = new ArrayList<>();
        reservationOpeningTime = Instant.now();
        reservationClosingTime = Instant.now();
        eventStartTime = Instant.now();
        eventEndTime = Instant.now();
    }

    public String getEventName() {
        return eventName;
    }

    public String getArtist() {
        return artist;
    }

    public ArrayList<AreaBean> getAreaBeans() {
        return areaBeans;
    }

    public Instant getReservationOpeningTime() { return reservationOpeningTime; }

    public Instant getReservationClosingTime() { return reservationClosingTime; }

    public Instant getEventStartTime() { return eventStartTime; }

    public Instant getEventEndTime() { return eventEndTime; }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAreaBeans(ArrayList<AreaBean> areaBeans) {
        this.areaBeans = areaBeans;
    }

    public void setReservationOpeningTime(Instant timestamp) { this.reservationOpeningTime = timestamp; }

    public void setReservationClosingTime(Instant timestamp) { this.reservationClosingTime = timestamp; }

    public void setEventStartTime(Instant timestamp) { this.eventStartTime = timestamp; }

    public void setEventEndTime(Instant timestamp) { this.eventEndTime = timestamp; }

    public CreateEvent toAvro(){
        ArrayList<Area> areas = new ArrayList<>();
        for(AreaBean areaBean: areaBeans){
            areas.add(areaBean.toAvro());
        }
        return new CreateEvent(
            this.artist, this.eventName, this.reservationOpeningTime, this.reservationClosingTime, this.eventStartTime, this.eventEndTime, areas
        );
    }
}
