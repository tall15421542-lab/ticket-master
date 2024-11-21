package lab.tall15421542.app;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.Area;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    public static void main(final String[] args) throws Exception {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, CreateEvent> createEventReqs = builder.stream(Topics.CREATE_EVENT.name(),
                        Consumed.with(Topics.CREATE_EVENT.keySerde(), Topics.CREATE_EVENT.valueSerde()));

        KStream<String, Area> createEventAreas = createEventReqs.flatMap(
                (eventName, createEvent) -> {
                    System.out.println(createEvent);
                    List<KeyValue<String, Area>> areas = new LinkedList<>();
                    for(Area area: createEvent.getAreas()){
                        areas.add(KeyValue.pair(eventName + area.getAreaId(), area));
                    }
                    return areas;
                }
        );

        createEventAreas.peek(
                (areaId, area) -> { log.info(areaId + " " + area); }
        );

        final Topology topology = builder.build();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-pipe");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        streams.close();
    }
}
