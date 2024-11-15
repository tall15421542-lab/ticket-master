package lab.tall15421542.app.domain.beans;

import java.util.ArrayList;

public class EventBean {
    private String eventName;
    private String artist;
    private ArrayList<Area> areas;

    public EventBean(){

    }

    public EventBean(String eventName, String artist, ArrayList<Area> areas){
        this.eventName = eventName;
        this.artist = artist;
        this.areas = areas;
    }

    public String getEventName() {
        return eventName;
    }

    public String getArtist() {
        return artist;
    }

    public ArrayList<Area> getAreas() {
        return areas;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAreas(ArrayList<Area> areas) {
        this.areas = areas;
    }
}
