# 8 instances Performance Report - v0.0.23
# 🧪 Performance Test Report

## ⚙️ Setting
* **Application Version**: [v0.0.23](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/8-instance-perf-v.0.0.23/appConfig)

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
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

## 200000 Reservations - 80 Sections
### Server-Side Trace (Sampled)
```bash
time go run main.go --host 10.140.0.30 -a 80 -env prod --http2 -n 200000 -c 20
```

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time |
|-----------|---------|---------|---------|------------|-----------------|
| 1st round | 0.009   | 0.595   | 0.735   | 0%         | 6.340           |
| 2nd round | 0.001   | 0.265   | 0.478   | 0%         | 6.012           |
| 3rd round | 0.002   | 0.197   | 0.347   | 0%         | 6.469           |
| 4th round | 0.002   | 0.203   | 0.354   | 0%         | 6.228           |
| 5th round | 0.002   | 0.193   | 0.310   | 0%         | 6.520           |
| **Avg**   | **0.003** | **0.291** | **0.445** | **0%**     | **6.314**         |

### Event service spans
![截圖 2025-07-26 晚上11.06.20](https://hackmd.io/_uploads/B1Cg1dGvlg.png)
* $588 \times 40 = 23520$ events flow to the event service.
![截圖 2025-07-26 晚上11.13.09](https://hackmd.io/_uploads/rkXclufwlx.png)
* $43 \times 40 = 1720$ events are processed by the random instance picked among 8 replicas.

## 200000 Reservations - 1 Sections
### Server-Side Trace (Sampled)
```bash
time go run main.go --host 10.140.0.30 -a 1 -env prod --http2 -n 200000 -c 20
```

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time |
|-----------|---------|---------|---------|------------|-----------------|
| 1st round | 0.002   | 0.166   | 0.293   | 0%         | 6.170           |
| 2nd round | 0.002   | 0.154   | 0.276   | 0%         | 6.127           |
| 3rd round | 0.002   | 0.130   | 0.234   | 0%         | 6.383           |
| 4th round | 0.002   | 0.161   | 0.253   | 0%         | 6.387           |
| 5th round | 0.002   | 0.045   | 0.193   | 0%         | 5.966           |
| **Avg**   | **0.002** | **0.131** | **0.250** | **0%**     | **6.207**         |

![截圖 2025-07-26 晚上10.19.24](https://hackmd.io/_uploads/S13lEDMvel.png)
![截圖 2025-07-26 晚上10.18.01](https://hackmd.io/_uploads/HJ8s7wMvgg.png)
* $122 \times 40 = 4880$ events flow to the event service.
* The workload is skewed - all events flow to a specific instance.

## Conclustion
* The completion time does not have much difference.
* The instance under skewed workload processes around $4880$ events, around $2.4\%$ of all reservations. This small fraction explains why the completion time remains nearly unchanged — most reservations are handled by the ticket and reservation services, which do not experience workload skew.
* More reservations are directly rejected by the reservation service under skewed workload, which explains the observed reduction in latency.