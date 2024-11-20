package lab.tall15421542.app.domain;

import lab.tall15421542.lab.app.avro.event.CreateEvent;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import java.lang.String;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

public class Schemas{
    public static SpecificAvroSerde<CreateEvent> CREATE_EVENT_VALUE_SERDE = new SpecificAvroSerde<>();
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
        public static Topic<String, CreateEvent> CREATE_EVENT;

        static {
            createTopics();
        }

        private static void createTopics(){
            CREATE_EVENT = new Topic<>("createEvent", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("createEvent", CREATE_EVENT);
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
        configureSerde(CREATE_EVENT_VALUE_SERDE, config, false);
    }

    private static void configureSerde(final Serde<?> serde, final Properties config, final Boolean isKey) {
        if (serde instanceof SpecificAvroSerde) {
            serde.configure(buildSchemaRegistryConfigMap(config), isKey);
        }
    }
}