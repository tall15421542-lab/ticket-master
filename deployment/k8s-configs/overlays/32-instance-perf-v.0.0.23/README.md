# 16 instances Performance Report - v0.0.23
# 🧪 Performance Test Report

## ⚙️ Setting
* **Application Version**: [v0.0.23](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/16-instance-perf-v.0.0.23/appConfig)
* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `64`
    * Memory: `32 GB`

* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 16       | 8000m | 16 GB  |
| Reservation Service | 16       | 2000m | 2 GB   |
| Event Service       | 16       | 2000m | 2 GB   |

* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 16

* **Observability**:
  * Trace Sampling Rate: 2.5%
  * Sampler: `parentbased_traceidratio`


## 400000 Reservations
### Client-Observed Server Processing Time
| Round     | P50 (s) | P95 (s) | P99 (s) | Error Rate | Completion Time |
|-----------|---------|---------|---------|------------|-----------------|
| 1st round | 0.193   | 2.026   | 2.656   | 0%         | 7.454           |
| 2nd round | 0.207   | 1.946   | 2.850   | 0%         | 7.605           |
| 3rd round | 0.212   | 2.003   | 2.717   | 0%         | 7.412           |
| 4th round | 0.248   | 1.993   | 2.560   | 0%         | 7.232           |
| 5th round | 0.216   | 2.157   | 3.082   | 0%         | 7.531           |
| **Avg**   | **0.215** | **2.025** | **2.773** | **0%**     | **7.447**         |

### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.104   | 1.488   | 1.962   | 0%         |
| 2nd round | 0.149   | 1.457   | 2.004   | 0%         |
| 3rd round | 0.137   | 1.421   | 1.924   | 0%         |
| 4th round | 0.114   | 1.463   | 1.921   | 0%         |
| 5th round | 0.159   | 1.596   | 2.160   | 0%         |
| **Avg**   | **0.133** | **1.485** | **1.994** | **0%**     |

![截圖 2025-05-19 下午2.00.46](https://hackmd.io/_uploads/SJizYS_-el.png)
![截圖 2025-05-19 下午2.01.45](https://hackmd.io/_uploads/HJPLtS_Wgl.png)
![截圖 2025-05-19 下午2.02.33](https://hackmd.io/_uploads/B1KFtHOble.png)

## 500000 Reservations
| Round     | P50 (s) | P95 (s) | P99 (s) | Error Rate | Completion Time |
|-----------|---------|---------|---------|------------|-----------------|
| 1st round | 0.266   | 2.417   | 3.378   | 0%         | 8.787           |
| 2nd round | 0.256   | 2.529   | 3.539   | 0%         | 8.995           |
| 3rd round | 0.218   | 3.117   | 2.717   | 0%         | 9.326           |
| **Avg**   | **0.247** | **2.688** | **3.211** | **0%**     | **9.036**         |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.144   | 1.820   | 2.323   | 0%         |
| 2nd round | 0.053   | 1.968   | 2.493   | 0%         |
| 3rd round | 0.090   | 2.087   | 2.900   | 0%         |
| **Avg**   | **0.096** | **1.958** | **2.572** | **0%**     |




