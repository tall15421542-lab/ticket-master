# Performance Report

## ⚙️ Setting
* **Application Version**: [v0.0.23.serial.resume](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23.serial.assume) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf-v0.0.23.serial.assume/appConfig)
* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 1        | 8000m | 8 GB  |
| Reservation Service | 1        | 4000m | 4 GB   |
| Event Service       | 1        | 4000m | 4 GB   |

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
    ```bash
    time go run main.go --host 10.140.0.9 -a 80 -env prod --http2 -n 50000
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `16`
    * Memory: `8 GB`
* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 1

* **Observability**:
  * Trace Sampling Rate: 10%
  * Sampler: `parentbased_traceidratio`

## 50000 Reservations
### v0.0.23 Baseline
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.156   | 2.138   | 2.296   | 0%         | 7                  |
| 2nd round | 1.074   | 2.154   | 2.505   | 0%         | 6                  |
| 3rd round | 1.107   | 2.188   | 2.276   | 0%         | 7                  |
| 4th round | 1.154   | 2.045   | 2.292   | 0%         | 6                  |
| 5th round | 1.323   | 2.019   | 2.245   | 0%         | 7                  |
| **Avg**   | **1.163** | **2.109** | **2.323** | **0%**     | **6.6**              |

### Serial Resume
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 3.426   | 6.144   | 6.219   | 0%         | 10                 |
| 2nd round | 2.782   | 5.534   | 5.620   | 0%         | 9                  |
| 3rd round | 3.065   | 5.241   | 5.345   | 0%         | 10                 |
| 4th round | 2.914   | 5.358   | 5.485   | 0%         | 9                  |
| 5th round | 2.423   | 5.063   | 5.229   | 0%         | 9                  |
| **Avg**   | **2.922** | **5.468** | **5.580** | **0%**     | **9.4**              |

## Conclusion
1. Latency reduced 60% with concurrent network I/O.
2. Throughput improves 40%.
