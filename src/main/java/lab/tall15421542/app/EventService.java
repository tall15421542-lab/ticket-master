package lab.tall15421542.app;

import org.apache.kafka.common.utils.Bytes;
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
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.kstream.Materialized;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.Area;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static AreaStatus toAreaStatus(String eventName, Area area){
        String areaId = area.getAreaId().toString();
        int rowCount = area.getRowCount(), colCount = area.getColCount();
        int availableSeats = rowCount * colCount;
        List<List<SeatStatus>> seats = new ArrayList<>();
        for(int i = 0 ; i < rowCount ; ++i){
            List<SeatStatus> row = new ArrayList<>();
            for(int j = 0 ; j < colCount ; ++j){
                row.add(j, new SeatStatus(i, j, true));
            }
            seats.add(row);
        }
        return new AreaStatus(
                eventName, areaId, area.getPrice(), rowCount, colCount, availableSeats, seats
        );
    }
    public static void main(final String[] args) throws Exception {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, CreateEvent> createEventReqs = builder.stream(Topics.CREATE_EVENT.name(),
                        Consumed.with(Topics.CREATE_EVENT.keySerde(), Topics.CREATE_EVENT.valueSerde()));

        KStream<String, AreaStatus> createEventAreas = createEventReqs.flatMap(
                (eventName, createEvent) -> {
                    List<KeyValue<String, AreaStatus>> areas = new LinkedList<>();
                    for(Area area: createEvent.getAreas()){
                        areas.add(KeyValue.pair(eventName + "#" + area.getAreaId(), toAreaStatus(eventName, area)));
                    }
                    return areas;
                }
        );

        createEventAreas.toTable(
                Materialized.<String, AreaStatus, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.AREA_STATUS.name())
                        .withKeySerde(Schemas.Stores.AREA_STATUS.keySerde())
                        .withValueSerde(Schemas.Stores.AREA_STATUS.valueSerde())
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
