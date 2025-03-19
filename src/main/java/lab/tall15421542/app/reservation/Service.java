package lab.tall15421542.app.reservation;

import lab.tall15421542.app.avro.event.AreaStatus;
import lab.tall15421542.app.avro.event.ReserveSeat;
import lab.tall15421542.app.avro.reservation.CreateReservation;
import lab.tall15421542.app.avro.reservation.Reservation;
import lab.tall15421542.app.avro.reservation.ReservationResult;
import lab.tall15421542.app.avro.reservation.StateEnum;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.domain.Schemas.Topics;
import lab.tall15421542.app.utils.Utils;
import org.apache.commons.cli.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

import static lab.tall15421542.app.utils.Utils.addShutdownHookAndBlock;

public class Service {
    private static final Logger log = LoggerFactory.getLogger(Service.class);
    private static final int MaxLRUEntries = 1000;

    public static void main(final String[] args) throws Exception {
        final Options opts = new Options();
        opts.addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("The directory for state storage").build())
                .addOption(Option.builder("c")
                        .longOpt("config").hasArg().desc("Config file path").required().build())
                .addOption(Option.builder("h")
                        .longOpt("help").hasArg(false).desc("Show usage information").build());

        final CommandLine cl = new DefaultParser().parse(opts, args);
        if (cl.hasOption("h")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Reservation Service", opts);
            return;
        }

        final String stateDir = cl.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String configFile = cl.getOptionValue("config");

        Properties config = Utils.readConfig(configFile);
        Schemas.configureSerdes(config);

        final Topology topology = createTopology();
        System.out.println(topology.describe());

        config.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "reservation-service");
        config.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.setProperty(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        config.setProperty(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG");

        KafkaStreams streams = new KafkaStreams(topology, config);
        streams.start();

        addShutdownHookAndBlock(() -> streams.close());
    }

    static Topology createTopology() {
        final StreamsBuilder builder = new StreamsBuilder();

        builder.globalTable(
                Topics.STATE_EVENT_AREA_STATUS.name(),
                Materialized.<String, AreaStatus>as(
                            Stores.lruMap(Schemas.Stores.EVENT_AREA_STATUS_CACHE.name(), MaxLRUEntries)
                        )
                        .withKeySerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.keySerde())
                        .withValueSerde(Schemas.Stores.EVENT_AREA_STATUS_CACHE.valueSerde())
        );

        KStream<String, CreateReservation> reservationRequests = builder.stream(
                Topics.COMMAND_RESERVATION_CREATE_RESERVATION.name(),
                Consumed.with(
                    Topics.COMMAND_RESERVATION_CREATE_RESERVATION.keySerde(),
                    Topics.COMMAND_RESERVATION_CREATE_RESERVATION.valueSerde()
                )
        );

        // key: reservationId
        KStream<String, Reservation> createReservationStream = reservationRequests.processValues(ReservationValueProcessor::new);

        KTable<String, Reservation> reservationTable = createReservationStream.toTable(
                Materialized.<String, Reservation, KeyValueStore<Bytes, byte[]>>as(Schemas.Stores.RESERVATION.name())
                        .withKeySerde(Schemas.Stores.RESERVATION.keySerde())
                        .withValueSerde(Schemas.Stores.RESERVATION.valueSerde())
                        .withCachingDisabled()
        );

        KStream<String, ReservationResult> reservationResults = builder.stream(
                Topics.RESPONSE_RESERVATION_RESULT.name(),
                Consumed.with(
                        Topics.RESPONSE_RESERVATION_RESULT.keySerde(),
                        Topics.RESPONSE_RESERVATION_RESULT.valueSerde()
                )
        );

        KStream<String, Reservation> updatedReservationStream = reservationResults.processValues(
                ReservationResultValueProcessor::new, Schemas.Stores.RESERVATION.name())
                .filter((key, value) -> value != null);

        KStream<String, Reservation> reservationStatusUpdatedStream = createReservationStream.merge(updatedReservationStream);

        final String PROCESSING = "processing", PROCESSED = "processed", DEFAULT = "default", PREFIX = "reservation-";
        Map<String, KStream<String, Reservation>> result = reservationStatusUpdatedStream.split(Named.as(PREFIX))
                .branch(
                        (reservationId, reservation) -> reservation.getState() == StateEnum.FAILED || reservation.getState() == StateEnum.RESERVED,
                        Branched.as(PROCESSED)
                ).branch(
                        (reservationId, reservation) -> reservation.getState() == StateEnum.PROCESSING,
                        Branched.as(PROCESSING)
                ).defaultBranch(Branched.as(DEFAULT));

        KStream<String, Reservation> processedReservation = result.get(PREFIX + PROCESSED);
        KStream<String, Reservation> processingReservation = result.get(PREFIX + PROCESSING);
        KStream<String, Reservation> invalidReservation = result.get(PREFIX + DEFAULT);

        KStream<String, ReserveSeat> processingReqs = processingReservation.map(
                (reservationId, reservation) -> KeyValue.pair(
                        reservation.getEventId().toString() + "#" + reservation.getAreaId(),
                        new ReserveSeat(
                                reservation.getReservationId(),
                                reservation.getEventId(),
                                reservation.getAreaId(),
                                reservation.getNumOfSeats(),
                                reservation.getNumOfSeat(),
                                reservation.getType(),
                                reservation.getSeats()
                        )
                )
        );

        processingReqs.to(Topics.COMMAND_EVENT_RESERVE_SEAT.name(), Produced.with(Topics.COMMAND_EVENT_RESERVE_SEAT.keySerde(), Topics.COMMAND_EVENT_RESERVE_SEAT.valueSerde()));
        processedReservation.to(Topics.STATE_USER_RESERVATION.name(), Produced.with(
                Topics.STATE_USER_RESERVATION.keySerde(),
                Topics.STATE_USER_RESERVATION.valueSerde()
        ));

        invalidReservation.foreach(
                (reservationId, reservation) -> System.out.println("reservation " + reservationId + "has invalid state " + reservation.getState())
        );

        return builder.build();
    }
}
