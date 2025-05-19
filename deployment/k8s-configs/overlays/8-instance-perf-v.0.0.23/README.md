# 8 instances Performance Report - v0.0.23
# 🧪 Performance Test Report

## ⚙️ Setting
* **Application Version**: [v0.0.23](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/8-instance-perf-v.0.0.23/appConfig)

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
    ```bash
    time go run main.go --host 10.140.0.30 -a 80 -env prod --http2 -n 200000 -c 4
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `32`
    * Memory: `16 GB`

* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 8        | 8000m | 16 GB  |
| Reservation Service | 8        | 2000m | 4 GB   |
| Event Service       | 8        | 2000m | 2 GB   |

* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 8

* **Observability**:
  * Trace Sampling Rate: 2.5%
  * Sampler: `parentbased_traceidratio`

## 200000 Reservations
### Client-Observed Server Processing Time

| Round     | P50 (s) | P95 (s) | P99 (s) | Error Rate | Completion Time |
|-----------|---------|---------|---------|------------|-----------------|
| 1st round | 0.229   | 0.513   | 0.671   | 0%         | 6.983           |
| 2nd round | 0.238   | 0.511   | 0.665   | 0%         | 6.735           |
| 3rd round | 0.271   | 0.667   | 0.665   | 0%         | 7.725           |
| 4th round | 0.444   | 1.001   | 1.211   | 0%         | 8.820           |
| 5th round | 0.455   | 0.909   | 1.085   | 0%         | 8.700           |
| **Avg**   | **0.327** | **0.720** | **0.859** | **0%**     | **7.793**         |

### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.003   | 0.286   | 0.346   | 0%         |
| 2nd round | 0.003   | 0.269   | 0.330   | 0%         |
| 3rd round | 0.003   | 0.308   | 0.433   | 0%         |
| 4th round | 0.001   | 0.228   | 0.321   | 0%         |
| 5th round | 0.001   | 0.196   | 0.274   | 0%         |
| **Avg**   | **0.002** | **0.257** | **0.341** | **0%**     |


![截圖 2025-05-18 晚上9.45.25](https://hackmd.io/_uploads/HyHYNPPWex.png)
![截圖 2025-05-18 晚上9.46.05](https://hackmd.io/_uploads/Hk2iVDD-lx.png)
