# Performance Report

## ⚙️ Setting
* **Application Version**: [v0.0.23.jersey.client](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23.jersey.client) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/2-instance-perf-v0.0.23.jersey.client/appConfig)
* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 2        | 8000m | 8 GB  |
| Reservation Service | 2        | 4000m | 4 GB   |
| Event Service       | 2        | 4000m | 4 GB   |

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
    ```bash
    time go run main.go --host 10.140.0.9 -a 80 -env prod --http2 -n 100000
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `16`
    * Memory: `8 GB`
* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 2

* **Observability**:
  * Trace Sampling Rate: 10%
  * Sampler: `parentbased_traceidratio`


## 100000 Reservations
### v0.0.23 Baseline
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 0.814   | 1.846   | 2.064   | 0%         | 8.37               |
| 2nd round | 0.748   | 2.015   | 2.299   | 0%         | 9.13               |
| 3rd round | 0.782   | 1.877   | 2.077   | 0%         | 8.02               |
| 4th round | 0.900   | 1.855   | 2.210   | 0%         | 8.31               |
| 5th round | 0.849   | 2.028   | 2.210   | 0%         | 9.00               |
| **Avg**   | **0.819** | **1.924** | **2.172** | **0%**     | **8.566**             |

Fetch from other host

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.824   | 1.835   | 2.034   | 0%         |
| 2nd round | 0.685   | 1.877   | 2.174   | 0%         |
| 3rd round | 0.795   | 1.857   | 2.054   | 0%         |
| 4th round | 0.552   | 1.508   | 2.240   | 0%         |
| 5th round | 0.835   | 2.027   | 2.214   | 0%         |
| **Avg**   | **0.738** | **1.821** | **2.143** | **0%**     |

### Jersey HTTP/1 Client
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.553   | 4.110   | 5.087   | 0%         | 18                 |
| 2nd round | 0.830   | 4.435   | 5.073   | 0%         | 14                 |
| 3rd round | 1.319   | 3.691   | 4.636   | 0.04%      | 18                 |
| 4th round | 1.290   | 4.447   | 5.320   | 0.03%      | 17                 |
| 5th round | 1.533   | 4.006   | 4.788   | 0%         | 15                 |
| **Avg**   | **1.305** | **4.138** | **4.981** | **0.014%** | **16.4**              |

Fetch from other host

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.750   | 4.275   | 5.209   | 0%         |
| 2nd round | 1.116   | 4.702   | 5.220   | 0%         |
| 3rd round | 1.470   | 4.057   | 4.738   | 0%         |
| 4th round | 1.491   | 4.630   | 5.392   | 0%         |
| 5th round | 1.533   | 4.269   | 4.951   | 0%         |
| **Avg**   | **1.472** | **4.387** | **5.102** | **0%**     |

## Conclusion
1. From trace for [fetchReservationFromOtherHost](https://github.com/tall15421542-lab/ticket-master/blob/440a2bb8234763f1f48f9593d8ec6758b4311bdd/src/main/java/lab/tall15421542/app/ticket/Service.java#L423-L435), the latency reduced 50-60% among p50, p95, p99.
2. Thoughput improves 91.4%.
