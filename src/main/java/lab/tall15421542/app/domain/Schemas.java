package lab.tall15421542.app.domain;

import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.Reservation;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import java.lang.String;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

public class Schemas{
    public static class Topic<K,V>{
        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;

        Topic(final String name, final Serde<K> keySerde, final Serde<V> valueSerde){
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        public Serde<K> keySerde() {
            return keySerde;
        }

        public Serde<V> valueSerde() {
            return valueSerde;
        }

        public String name() {
            return this.name;
        }

        public String toString() {
            return this.name;
        }
    }

    public static class Topics {
        public final static Map<String, Topic<?, ?>> ALL = new HashMap<>();
        public static Topic<String, CreateEvent> COMMAND_EVENT_CREATE_EVENT;
        public static Topic<String, ReserveSeat> COMMAND_EVENT_RESERVE_SEAT;
        public static Topic<String, ReservationResult> RESPONSE_RESERVATION_RESULT;
        public static Topic<String, AreaStatus> STATE_EVENT_AREA_STATUS;
        public static Topic<String, CreateReservation> COMMAND_RESERVATION_CREATE_RESERVATION;
        public static Topic<String, Reservation> STATE_USER_RESERVATION;

        static {
            createTopics();
        }

        private static void createTopics(){
            // key: event name
            COMMAND_EVENT_CREATE_EVENT = new Topic<>("command.event.create_event", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("command.event.create_event", COMMAND_EVENT_CREATE_EVENT);

            // key: eventId + "#" + areaId
            COMMAND_EVENT_RESERVE_SEAT = new Topic<>("command.event.reserve_seat", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("command.event.reserve_seat", COMMAND_EVENT_RESERVE_SEAT);

            // key: reservation id
            RESPONSE_RESERVATION_RESULT = new Topic<>("response.reservation.result", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("response.reservation.result", RESPONSE_RESERVATION_RESULT);

            // key: eventId + "#" + areaId
            STATE_EVENT_AREA_STATUS = new Topic<>("state.event.area_status", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("state.event.area_status", STATE_EVENT_AREA_STATUS);

            // key: user id
            COMMAND_RESERVATION_CREATE_RESERVATION = new Topic<>("command.reservation.create_reservation", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("command.reservation.create_reservation", COMMAND_RESERVATION_CREATE_RESERVATION);

            // key user id
            STATE_USER_RESERVATION = new Topic<>("state.user.reservation", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("state.user.reservation", STATE_USER_RESERVATION);
        }
    }

    public static class Store<K,V>{
        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;

        Store(final String name, final Serde<K> keySerde, final Serde<V> valueSerde){
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        public Serde<K> keySerde() {
            return keySerde;
        }

        public Serde<V> valueSerde() {
            return valueSerde;
        }

        public String name() {
            return this.name;
        }

        public String toString() {
            return this.name;
        }
    }

    public static class Stores {
        public final static Map<String, Store<?, ?>> ALL = new HashMap<>();
        public static Store<String, AreaStatus> AREA_STATUS;
        public static Store<String, Reservation> RESERVATION;
        public static Store<String, AreaStatus> EVENT_AREA_STATUS_CACHE;

        static {
            createStores();
        }

        private static void createStores(){
            // Key: eventId + "#" + areaId
            AREA_STATUS = new Store<>("AreaStatus", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("AreaStatus", AREA_STATUS);

            // Key: reservation id
            RESERVATION = new Store<>("Reservation", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("Reservation", RESERVATION);

            // key: eventId + "#" areaId
            EVENT_AREA_STATUS_CACHE = new Store<>("eventAreaStatusCache", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("eventAreaStatusCache", EVENT_AREA_STATUS_CACHE);
        }
    }

    public static Map<String, ?> buildSchemaRegistryConfigMap(final Properties config) {
        final HashMap<String, String> map = new HashMap<>();
        if (config.containsKey(SCHEMA_REGISTRY_URL_CONFIG))
            map.put(SCHEMA_REGISTRY_URL_CONFIG, config.getProperty(SCHEMA_REGISTRY_URL_CONFIG));
        return map;
    }

    public static void configureSerdes(final Properties config) {
        Topics.createTopics(); //wipe cached schema registry
        for (final Topic<?, ?> topic : Topics.ALL.values()) {
            configureSerde(topic.keySerde(), config, true);
            configureSerde(topic.valueSerde(), config, false);
        }

        Stores.createStores(); //wipe cached schema registry
        for(final Store<?, ?> store: Stores.ALL.values()){
            configureSerde(store.keySerde(), config, true);
            configureSerde(store.valueSerde(), config, false);
        }
    }

    private static void configureSerde(final Serde<?> serde, final Properties config, final Boolean isKey) {
        if (serde instanceof SpecificAvroSerde) {
            serde.configure(buildSchemaRegistryConfigMap(config), isKey);
        }
    }
}