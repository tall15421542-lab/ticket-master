package lab.tall15421542.app.domain.beans;

import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.Area;
import java.util.ArrayList;
import java.time.Instant;

public class EventBean {
    private String eventName;
    private String artist;
    private ArrayList<AreaBean> areas;
    private Instant reservationOpeningTime;
    private Instant reservationClosingTime;
    private Instant eventStartTime;
    private Instant eventEndTime;

    public EventBean(){
        eventName = new String();
        artist = new String();
        areas = new ArrayList<>();
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

    public ArrayList<AreaBean> getAreas() {
        return areas;
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

    public void setAreas(ArrayList<AreaBean> areas) {
        this.areas = areas;
    }

    public void setReservationOpeningTime(Instant timestamp) { this.reservationOpeningTime = timestamp; }

    public void setReservationClosingTime(Instant timestamp) { this.reservationClosingTime = timestamp; }

    public void setEventStartTime(Instant timestamp) { this.eventStartTime = timestamp; }

    public void setEventEndTime(Instant timestamp) { this.eventEndTime = timestamp; }

    public CreateEvent toAvro(){
        ArrayList<Area> avro_areas = new ArrayList<>();
        for(AreaBean areaBean: areas){
            avro_areas.add(areaBean.toAvro());
        }
        return new CreateEvent(
            this.artist, this.eventName, this.reservationOpeningTime, this.reservationClosingTime, this.eventStartTime, this.eventEndTime, avro_areas
        );
    }
}
