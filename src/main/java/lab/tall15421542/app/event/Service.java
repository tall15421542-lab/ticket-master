package lab.tall15421542.app.event;

import lab.tall15421542.app.avro.event.*;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.utils.Utils;
import org.apache.commons.cli.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static lab.tall15421542.app.utils.Utils.addShutdownHookAndBlock;

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

        KTable<String, AreaStatus> areaStatus = createEventAreas.toTable(
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
                .addOption(Option.builder("c")
                        .longOpt("config").hasArg().desc("Config file path").build())
                .addOption(Option.builder("sc")
                        .longOpt("stream-config").hasArg().desc("stream config file path").build())
                .addOption(Option.builder("h")
                        .longOpt("help").hasArg(false).desc("Show usage information").build());

        final CommandLine cl = new DefaultParser().parse(opts, args);
        if (cl.hasOption("h")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Event Service", opts);
            return;
        }

        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String configFile = cl.getOptionValue("config", "../client.dev.properties");
        final String streamConfigFile = cl.getOptionValue("stream-config", "");

        Properties baseConfig = Utils.readConfig(configFile);
        Schemas.configureSerdes(baseConfig);

        Topology topology = createTopology();
        System.out.println(topology.describe());

        Properties streamConfig = new Properties();
        streamConfig.putAll(baseConfig);
        if(!streamConfigFile.equals("")){
            streamConfig.putAll(Utils.readConfig(streamConfigFile));
        }
        streamConfig.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "event-service");
        streamConfig.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        streamConfig.setProperty(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        streamConfig.putIfAbsent(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "20");
        streamConfig.setProperty(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once_v2");

        KafkaStreams streams = new KafkaStreams(topology, streamConfig);
        streams.start();

        addShutdownHookAndBlock(() -> streams.close());
    }
}
