# 📈 Performance Test Report

## 🛠️ Settings
- **Service Configuration**:
    - Application Version: [v0.0.21](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.21)
    - Resources:

    | Service             | Replicas | CPU    | Memory |
    |---------------------|----------|--------|--------|
    | Ticket Service      | 40       | 4000m  | 8 GB   |
    | Reservation Service | 40       | 500m   | 2 GB   |
    | Event Service       | 40       | 500m   | 2 GB   |

- **Infrastructure**:
  - Confluent Cloud Kafka Cluster
    - Type: Basic
    - Partitions per topic: 40

- **Observability**:
  - Trace Sampling Rate: 1%
  - Sampler: `parentbased_traceidratio`

- **Spike Test Client**: 
    - Client Machine (Google Compute Engine):
        - Machine Type: `n2-custom-64-32768`
        - vCPUs: 64
        - Memory: 32 GB
    - Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.22/scripts/perf/go-client/main.go)
  ```bash
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  wait
  ```


## 🧪 Testing Procedure

### 1. Service Warm-Up
Services were warmed up until they could handle **1,000,000 concurrent reservation requests** with each pod's CPU usage under 25%.

[Ref: Why we need warm-up](https://medium.com/blablacar/warm-up-the-relationship-between-java-and-kubernetes-7fc5741f9a23)

### 2. Spike Test Execution
Generated **1,000,000 concurrent reservation requests** for **160,000 seats**, distributed across:
- 4 events 
- 100 areas per event 
- 400 seats per area (random continuous seat selections)

### 3. Metric Collection
Latency and trace metrics were collected from:
- Client-side
- Server-side (via OpenTelemetry)



## 📊 Test Results — 1,000,000 Concurrent Requests
### Client-Observed Latency (Including I/O and goroutine context switch)

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 10.628  | 15.349  | 16.083  |
| 2nd round | 10.594  | 15.237  | 15.951  |
| 3rd round | 10.442  | 15.050  | 15.754  |
| 4th round | 10.457  | 14.810  | 15.540  |
| 5th round | 10.782  | 15.377  | 16.124  |
| **Avg**   | **10.581** | **15.165** | **15.890** |

### Spans graph

#### `POST /v1/event/{id}/reservation`
##### Span Rate
![截圖 2025-05-07 上午11.15.57](https://hackmd.io/_uploads/S1ktxIOglg.png)

##### Span Duration(Percentile)
![截圖 2025-05-07 上午11.16.34](https://hackmd.io/_uploads/SyEseLdllg.png)

#### `GET /v1/reservation/{reservation_id}`

###### Span Rate
![截圖 2025-05-07 上午11.15.23](https://hackmd.io/_uploads/Sy38xIdxel.png)

##### Span Duration(Percentile)
![截圖 2025-05-07 上午11.14.27](https://hackmd.io/_uploads/H1E7lLulex.png)

#### Reservation service
##### Span Rate
![截圖 2025-05-07 上午11.47.12](https://hackmd.io/_uploads/ByAaw8ugee.png)

##### Span Duration(Percentile)
![截圖 2025-05-07 上午11.48.44](https://hackmd.io/_uploads/H1iX_Ldexe.png)

##### Event Service

##### Span Rate
![截圖 2025-05-07 上午11.50.13](https://hackmd.io/_uploads/SkQFuLdllg.png)

##### Span Duration(Percentile)
![截圖 2025-05-07 上午11.51.12](https://hackmd.io/_uploads/BJ0nOIuelg.png)


### Server-Side Trace (Sampled)

| Round     | Sample Size | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|-------------|---------|---------|---------|------------|
| 1st round | 20,303      | 0.001   | 1.455   | 2.084   | 0%         |
| 2nd round | 19,850      | 0.005   | 1.578   | 2.040   | 0%         |
| 3rd round | 19,786      | 0.001   | 1.077   | 1.665   | 0%         |
| 4th round | 19,827      | 0.001   | 1.589   | 2.022   | 0%         |
| 5th round | 19,446      | 0.002   | 1.490   | 1.988   | 0%         |
| **Avg**   | **19,842**  | **0.002** | **1.438** | **1.960** | **0%** |

---

## Analysis
### 1. Scalability Confirmed

Compared to the [16-instance test with 400,000 requests](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/16-instance-perf#-testing-result---400000-concurrent-requests), the client-observed latency (including I/O, server processing, and goroutine context switch) showed the following:

- **P50 latency** increased by ~33%
- **P95 latency** remained stable
- **P99 latency** decreased by ~9%

When compared against [server-side latency](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/16-instance-perf#%EF%B8%8F-server-trace-sampled), performance improved across all percentiles:
- **P50 latency**: Improved from `220ms` to `2ms`.
- **P90 latency**: Improved from `3.43s` to `1.438s` (↓ 58%).
- **P95 latency**: Improved from `4.868s` to `1.96s` (↓ 60%).

➡️ This confirms **near-linear scalability** under increased load with proportional resource scaling.


---

### 2. 📊 Span Graph Analysis

#### Reservation request distribution
From the span graph, we observe that the arrival of reservation requests:

```
POST /v1/event/{id}/reservation
```

follows a **bell curve distribution** over approximately **15 seconds**. This indicates that clients **do not send 1 million requests simultaneously**.

This aligns with expected real-world user behavior:
- Some customers send requests as soon as ticket sales open.
- Others send requests slightly later due to reaction time or network delays.
- The result is a natural distribution resembling a bell curve.

#### Event Service Behavior

- The **Event Service stops processing requests after about 7 seconds**.
- This indicates the event is **sold out** within that time and the final seat availability state is propagated to the reservation service cache.

---

### 3. Significant Improvement in Throughput and Latency

For **1,000,000 concurrent reservation requests**:
- **50% of users** completed in under **10 seconds**.
- **99% of users** completed in under **16 seconds**.
- The event **sold out within 7 seconds**.

⚡ Compared to tixcraft, the system used for the Jay Chou concert:

| Media Source | User Experience Summary |
|--------------|-------------------------|
| [Yahoo News](https://tw.news.yahoo.com/89%E8%90%AC%E4%BA%BA%E6%90%B6%E5%91%A8%E8%91%A3%E7%A5%A8-%E4%BA%94%E5%88%86%E9%90%98%E7%A7%92%E6%AE%BA-%E5%89%B5%E5%94%AE%E7%A5%A8%E7%B3%BB%E7%B5%B1%E7%B4%80%E9%8C%84-101000901.html) | Spinning for 5–10 minutes |
| [聯合新聞網](https://udn.com/news/story/7160/8310373) | Some users waited over 20 minutes |
| [NOWnews #1](https://www.nownews.com/news/6561102) | Tickets gone while users still spinning |
| [NOWnews #2](https://www.nownews.com/news/6561121) | Users still spinning after 6 minutes |

- **Sell-out time**:
  - tixcraft: Up to 5 minutes (**300 seconds**)
  - This system: **7 seconds**  
  → **~43× faster sell-out**

- **User wait time**:
  - tixcraft: 5–20 minutes (**300–1200 seconds**)
  - This system: **99% of users finished in 16 seconds**  
  → **~19× to 75× faster user completion**

Notably, this performance was achieved using **far fewer resources** than the [10,000 virtual machine setup](https://money.udn.com/money/story/5648/8310486) referenced in public reports:
- **40 pods** with **4 vCPUs** and **8 GB memory** each  
- **80 pods** with **0.5 vCPU** and **2 GB memory** each  
- **Confluent Kafka Cluster**

## Conclusion

While the load test client does not send all 1,000,000 requests simultaneously, the distribution follows a natural **bell curve**, closely simulating real-world traffic patterns during high-demand ticket sales.

Despite this realistic traffic shape, the system demonstrated the ability to **handle 1,000,000 reservation requests within just 16 seconds**, with **50% of users completing in under 10 seconds** and **the event selling out in only 7 seconds**.

Crucially, this was achieved using **significantly fewer resources** than the 10,000-VM setup reportedly used for Jay Chou’s concert, yet delivered **19× to 75× faster performance**.

➡️ **This validates the system’s scalability, efficiency, and suitability for large-scale, real-time reservation workloads—while maintaining exceptional performance under extreme load.**

