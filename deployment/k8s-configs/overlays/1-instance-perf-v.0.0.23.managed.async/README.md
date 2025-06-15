# Performance Report

## ⚙️ Setting
* **Application Version**: [v.0.0.23.managed.async](https://github.com/tall15421542-lab/ticket-master/tree/v.0.0.23.managed.async) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf-v.0.0.23.managed.async/appConfig)
* **Services**:


| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 1        | 8000m | 8 GB   |
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
### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.170   | 2.335   | 2.566   | 0%         | 7                  |
| 2nd round | 1.074   | 2.154   | 2.505   | 0%         | 6                  |
| 3rd round | 1.107   | 2.233   | 2.467   | 0%         | 7                  |
| 4th round | 1.154   | 2.045   | 2.292   | 0%         | 6                  |
| 5th round | 1.174   | 2.349   | 2.586   | 0%         | 7                  |
| **Avg**   | **1.136** | **2.223** | **2.483** | **0%**     | **6.6**              |

### With ManagedAsync
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.904   | 4.117   | 4.560   | 0%         | 10                 |
| 2nd round | 1.895   | 4.308   | 4.669   | 0%         | 9                  |
| 3rd round | 1.755   | 3.954   | 4.393   | 0%         | 9                  |
| 4th round | 1.638   | 3.784   | 4.030   | 0%         | 9                  |
| 5th round | 1.655   | 3.684   | 4.212   | 0%         | 9                  |
| **Avg**   | **1.769** | **3.969** | **4.373** | **0%**     | **9.2**              |

## Conclusion
With virtual thread 
* the RPS improve 40% RPS(From 5434 to 7575)
* P95 reduced 44% latency
* P90 reduced 44% latency
* p50 reduced 36% latenct 
