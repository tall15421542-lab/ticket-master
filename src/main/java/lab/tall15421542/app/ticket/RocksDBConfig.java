package lab.tall15421542.app.ticket;

import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.Options;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;

import java.util.Map;

public class RocksDBConfig implements RocksDBConfigSetter {
    @Override
    public void setConfig(String storeName, Options options, Map<String, Object> configs) {
        // Enable RocksDB statistics
        Statistics statistics = new Statistics();
        statistics.setStatsLevel(StatsLevel.ALL);

        options.setStatistics(statistics);
        options.setStatsDumpPeriodSec(10); // Dump statistics every 10 seconds (optional)

        System.out.println("RocksDB statistics enabled for store: " + storeName);
    }

    @Override
    public void close(String storeName, Options options) {
        System.out.println("Closing RocksDB options for store: " + storeName);
    }
}
