# Performance Report

## ⚙️ Setting
* **Application Version**: [v0.0.23](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf-v.0.0.23.no.batch/appConfig)
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
| 1st round | 10      | 10      | 10      | 85.58%     | 231                |
| 2nd round | 10      | 10      | 10      | 84.52%     | 229                |
| 3rd round | 10      | 10      | 10      | 84.26%     | 231                |
| 4th round | 10      | 10      | 10      | 84.52%     | 229                |
| 5th round | 10      | 10      | 10      | 84.12%     | 228                |
| **Avg**   | **10.000** | **10.000** | **10.000** | **84.60%** | **229.6**            |

## Conclusion
1. Without batch, completion time increased from 6.6 seconds to 229.6 seconds, throughput dropped dramatically by approximately 35 times, severely limiting the system’s ability to process reservations efficiently.
2. The error represents a timeout, meaning that even with exponential backoff retries, 84.6% of requests fail to complete within 10 seconds.
