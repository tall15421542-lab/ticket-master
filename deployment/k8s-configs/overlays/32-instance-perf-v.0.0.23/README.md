# 32 instances Performance Report - v0.0.23

## Setting
* **Application Version**: [v0.0.23](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.23) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/32-instance-perf-v.0.0.23/appConfig)

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
    ```bash
    go run main.go --host 10.140.0.126 -a 80 -env prod --http2 -n 200000 -c 4 & \
    go run main.go --host 10.140.0.126 -a 80 -env prod --http2 -n 200000 -c 4 & \
    go run main.go --host 10.140.0.126 -a 80 -env prod --http2 -n 200000 -c 4 & \
    go run main.go --host 10.140.0.126 -a 80 -env prod --http2 -n 200000 -c 4 & \
    go run main.go --host 10.140.0.126 -a 80 -env prod --http2 -n 200000 -c 4 & \
    time wait
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `64`
    * Memory: `32 GB`

* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 32       | 6000m | 12 GB  |
| Reservation Service | 32       | 2000m | 2 GB   |
| Event Service       | 32       | 2000m | 2 GB   |

* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 32
    
* **Observability**:
  * Trace Sampling Rate: 1%
  * Sampler: `parentbased_traceidratio`

## 800000 Reservations
### Client-Observed Server Processing Time

| Round     | P50 (s)   | P95 (s)   | P99 (s)   | Completion Time (s) |
| --------- | --------- | --------- | --------- | ------------------- |
| 1st round | 1.041     | 1.826     | 2.119     | 11.991              |
| 2nd round | 0.899     | 1.681     | 1.964     | 11.153              |
| 3rd round | 0.865     | 1.548     | 1.801     | 11.290              |
| 4th round | 0.834     | 1.652     | 2.002     | 11.581              |
| 5th round | 0.837     | 1.500     | 2.020     | 12.280              |
| **Avg**   | **0.895** | **1.641** | **1.961** | **11.659**          |

### Server-Side Trace (Sampled)

| Round     | P50 (s)   | P90 (s)   | P95 (s)   | Completion Time (s) |
| --------- | --------- | --------- | --------- | ------------------- |
| 1st round | 0.001     | 0.126     | 0.415     | 12                  |
| 2nd round | 0.001     | 0.162     | 0.407     | 11                  |
| 3rd round | 0.001     | 0.196     | 0.435     | 11                  |
| 4th round | 0.001     | 0.265     | 0.554     | 11                  |
| 5th round | 0.001     | 0.202     | 0.575     | 12                  |
| **Avg**   | **0.001** | **0.190** | **0.477** | **11.4**            |

### HTTP Request
![截圖 2025-05-20 下午2.08.![截圖 2025-05-20 下午2.09.47](https://hackmd.io/_uploads/Bk9nhcYZel.png)
27](https://hackmd.io/_uploads/S12D3qtZex.png)
POST /v1/event/{id}/reservation Span Rate Graph(250ms)
![截圖 2025-05-20 下午2.11.30](https://hackmd.io/_uploads/SygXT9tWex.png)
GET /v1/reservation/{reservation_id} Span duration Percentile Graph(250ms)
![截圖 2025-05-20 下午2.10.55](https://hackmd.io/_uploads/rkaea5Kbgx.png)


### Producer Publish
![截圖 2025-05-20 下午2.13.27](https://hackmd.io/_uploads/rJL56qKZxg.png)
![截圖 2025-05-20 下午2.13.51](https://hackmd.io/_uploads/B1nspcFWgx.png)
![截圖 2025-05-20 下午2.14.12](https://hackmd.io/_uploads/rkzppqFbex.png)

### Kafka Streams Process
![截圖 2025-05-20 下午2.15.47](https://hackmd.io/_uploads/ByWXA5t-gg.png)
![截圖 2025-05-20 下午2.16.36](https://hackmd.io/_uploads/Bkz809KWxl.png)
![截圖 2025-05-20 下午2.16.05](https://hackmd.io/_uploads/rJMVRqFWle.png)

### Reservation Journey
![截圖 2025-05-20 下午2.26.56](https://hackmd.io/_uploads/SkA2eit-gl.png)


## 1000000 Reservations
### Client-Observed Server Processing Time

| Round     | P50 (s)   | P95 (s)   | P99 (s)   | Completion Time (s) | P99 Completion Time (s) |
| --------- | --------- | --------- | --------- | ------------------- | ----------------------- |
| 1st round | 1.089     | 2.090     | 2.580     | 13.424              | 12.347                  |
| 2nd round | 0.965     | 1.841     | 2.492     | 14.613              | 12.406                  |
| 3rd round | 0.962     | 1.608     | 2.024     | 12.434              | 11.473                  |
| 4th round | 0.975     | 1.895     | 2.293     | 12.506              | 11.828                  |
| 5th round | 0.879     | 1.826     | 2.258     | 12.227              | 11.378                  |
| **Avg**   | **0.974** | **1.852** | **2.329** | **13.041**          | **11.886**              |

### Server-Side Trace (Sampled)

| Round     | P50 (s)   | P90 (s)   | P95 (s)   | Completion Time (s) |
| --------- | --------- | --------- | --------- | ------------------- |
| 1st round | 0.001     | 0.495     | 0.864     | 13                  |
| 2nd round | 0.001     | 0.383     | 0.773     | 12                  |
| 3rd round | 0.001     | 0.250     | 0.717     | 11                  |
| 4th round | 0.001     | 0.461     | 0.768     | 12                  |
| 5th round | 0.001     | 0.262     | 0.565     | 11                  |
| **Avg**   | **0.001** | **0.370** | **0.737** | **11.8**            |

### HTTP Request
![截圖 2025-05-20 下午4.54.05](https://hackmd.io/_uploads/SypN76K-xg.png)
POST /v1/event/{id}/reservation Span Rate Graph(250ms)
![截圖 2025-05-20 下午4.52.57](https://hackmd.io/_uploads/S1_emaFZgg.png)
GET /v1/reservation/{reservation_id} Span duration Percentile Graph(250ms)
![截圖 2025-05-20 下午4.53.37](https://hackmd.io/_uploads/HJ1mXaYZlx.png)

### Producer Publish
![截圖 2025-05-20 下午4.56.20](https://hackmd.io/_uploads/ryw6QpFWlx.png)
![截圖 2025-05-20 下午4.56.53](https://hackmd.io/_uploads/Byr14aF-gg.png)
![截圖 2025-05-20 下午4.57.33](https://hackmd.io/_uploads/H1hWVpYbxe.png)


### Kafka Streams Process
![截圖 2025-05-20 下午4.59.20](https://hackmd.io/_uploads/H1K_4aFWxg.png)
![截圖 2025-05-20 下午4.59.35](https://hackmd.io/_uploads/BJUtN6YZel.png)
![截圖 2025-05-20 下午5.00.06](https://hackmd.io/_uploads/HyLs4aFWee.png)

### Reservation Journey
![截圖 2025-05-20 下午5.29.56](https://hackmd.io/_uploads/Hk4iiptbge.png)
![截圖 2025-05-20 下午5.32.19](https://hackmd.io/_uploads/SyMN3pFbel.png)

### Sold out Time - 3 seconds
Event Service Span Rate Graph
![截圖 2025-05-20 下午5.38.15](https://hackmd.io/_uploads/HJtcapKZeg.png)

## Analysis
1. The system processed 1 million reservations within 11.8 seconds, equivalent to 84745 RPS, with a latency of p95 735 ms.
2. The system sold out 160,000 tickets within 3 seconds.
3. Despite linger.ms=100, publish latencies between the Event Service and Reservation Service exceed 250 ms at the p95 level. This suggests either broker-side I/O contention or producer buffer pressure. Since repartitioning occurs at this stage, each producer must send messages to all 32 partitions, increasing memory usage and reducing batching efficiency. This is a key area for optimization — improving producer configurations and broker/network I/O throughput could significantly reduce latency.
4. Kafka Stream processing in under 400 µs in p95. This application is I/O-bound.
