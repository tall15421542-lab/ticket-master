package lab.tall15421542.app.event;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;


import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.avro.event.CreateEvent;
import lab.tall15421542.app.avro.event.Area;
import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.SeatStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.reservation.ReservationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Service {
    private static final Logger log = LoggerFactory.getLogger(Service.class);

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

    public interface ReservationStrategy {
        ReservationResult reserve(AreaStatus areaStatus, ReserveSeat req);
    }

    public static Topology createTopology(){
        final StreamsBuilder builder = new StreamsBuilder();

        // Create Event Flow
        KStream<String, CreateEvent> createEventReqs = builder.stream(Topics.COMMAND_EVENT_CREATE_EVENT.name(),
                Consumed.with(Topics.COMMAND_EVENT_CREATE_EVENT.keySerde(), Topics.COMMAND_EVENT_CREATE_EVENT.valueSerde()));

        KStream<String, AreaStatus> createEventAreas = createEventReqs.flatMap(
                (eventName, createEvent) -> {
                    List<KeyValue<String, AreaStatus>> areas = new LinkedList<>();
                    for(Area area: createEvent.getAreas()){
                        areas.add(KeyValue.pair(eventName + "#" + area.getAreaId(), toAreaStatus(eventName, area)));
                    }
                    return areas;
                }
        );

        KStream<String, AreaStatus> repartitionedCreateEventAreas = createEventAreas.repartition(
                Repartitioned.<String,AreaStatus>numberOfPartitions(10)
                        .withKeySerde(Schemas.Stores.AREA_STATUS.keySerde())
                        .withValueSerde(Schemas.Stores.AREA_STATUS.valueSerde()));

        KTable<String, AreaStatus> areaStatus = repartitionedCreateEventAreas.toTable(
                Materialized.<String, AreaStatus, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.AREA_STATUS.name())
                        .withKeySerde(Schemas.Stores.AREA_STATUS.keySerde())
                        .withValueSerde(Schemas.Stores.AREA_STATUS.valueSerde())
        );

        // Reservation Flow
        KStream<String, ReserveSeat> reserveSeatReqs = builder.stream(Topics.COMMAND_EVENT_RESERVE_SEAT.name(),
                Consumed.with(Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde(), Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde()));

        KStream<String, ReservationResult> reserveResult = reserveSeatReqs.transform(new TransformerSupplier() {
            public Transformer get() {
                return new ReserveSeatTransformer();
            }
        }, Schemas.Stores.AREA_STATUS.name());

        reserveResult.to(Topics.RESPONSE_RESERVATION_RESULT.name(), Produced.with(
                Topics.RESPONSE_RESERVATION_RESULT.keySerde(),
                Topics.RESPONSE_RESERVATION_RESULT.valueSerde()
        ));

        // emit event area status state changes
        areaStatus.toStream().to(Topics.STATE_EVENT_AREA_STATUS.name(), Produced.with(
                Topics.STATE_EVENT_AREA_STATUS.keySerde(),
                Topics.STATE_EVENT_AREA_STATUS.valueSerde()
        ));

        return builder.build();
    }
    
    public static void main(final String[] args) throws Exception {
        final Options opts = new Options();
        opts.addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("The directory for state storage").build())
                .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Show usage information").build());

        final CommandLine cl = new DefaultParser().parse(opts, args);
        if (cl.hasOption("h")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Event Service", opts);
            return;
        }

        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");

        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        Topology topology = createTopology();
        System.out.println(topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "event-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                MyDeserializationExceptionHandler.class.getName());
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        new BufferedReader(new InputStreamReader(System.in)).readLine();
        streams.close();
    }

    public static class MyDeserializationExceptionHandler implements DeserializationExceptionHandler {
        @Override
        public DeserializationHandlerResponse handle(ProcessorContext context,  ConsumerRecord<byte[],byte[]> record, Exception e) {
            // Log the error and continue processing
            System.err.println("Error deserializing record: " + e.getMessage());
            return DeserializationHandlerResponse.CONTINUE;  // Continue processing other records
        }

        @Override
        public void configure(Map<String, ?> configs) {
            // You can access configuration properties here if needed
            System.out.println("Configuring MyDeserializationExceptionHandler with configs: " + configs);
        }
    }

}
