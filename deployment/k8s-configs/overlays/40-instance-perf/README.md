# 📈 Performance Test Report

## 🛠️ Settings

- **Application Version**: [v0.0.21](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.21)
- **Spike Test Client**:  
  - Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.22/scripts/perf/go-client/main.go)

  ```bash
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  go run main.go --host 10.140.0.41 -a 100 -env prod --http2 -n 250000 -c 4 & \
  wait
  ```

  - **Client Machine (Google Compute Engine)**:
    - Machine Type: `n2-custom-64-32768`
    - vCPUs: 64
    - Memory: 32 GB

- **Service Configuration**:

| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 40       | 4000m  | 8 GB   |
| Reservation Service | 40       | 500m   | 2 GB   |
| Event Service       | 40       | 500m   | 2 GB   |

- **Infrastructure**:
  - **Confluent Cloud Kafka Cluster**
    - Type: Basic
    - Partitions per topic: 40

- **Observability**:
  - Trace Sampling Rate: 1%
  - Sampler: `parentbased_traceidratio`

---

## 🧪 Testing Procedure

### 1. Service Warm-Up
Services were warmed up until they could handle **1,000,000 concurrent reservation requests** with each pod's CPU usage under 25%.

### 2. Spike Test Execution
Generated 1,000,000 concurrent reservation requests for:
- 4 events  
- 100 areas per event  
- 400 seats per area (random continuous seat selections)
- **160,000 seats** in total

### 3. Metric Collection
Latency and trace metrics were collected from:
- Client-side
- Server-side (via OpenTelemetry)



## 📊 Test Results — 1,000,000 Concurrent Requests

### ✅ Client-Observed Latency(Per request)

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 1.608   | 2.961   | 3.630   |
| 2nd round | 1.633   | 3.222   | 4.150   |
| 3rd round | 1.539   | 2.636   | 3.133   |
| 4th round | 1.563   | 2.830   | 3.473   |
| 5th round | 1.615   | 2.890   | 3.546   |
| **Avg**   | **1.592** | **2.908** | **3.586** |

---

### 🔁 Client-Observed Latency (Including I/O and goroutine context switch)

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 10.628  | 15.349  | 16.083  |
| 2nd round | 10.594  | 15.237  | 15.951  |
| 3rd round | 10.442  | 15.050  | 15.754  |
| 4th round | 10.457  | 14.810  | 15.540  |
| 5th round | 10.782  | 15.377  | 16.124  |
| **Avg**   | **10.581** | **15.165** | **15.890** |

### Spans graph

#### POST /v1/event/{id}/reservation

##### Span Rate
![截圖 2025-05-07 上午11.15.57](https://hackmd.io/_uploads/S1ktxIOglg.png)

##### Span Duration(Percentile)
![截圖 2025-05-07 上午11.16.34](https://hackmd.io/_uploads/SyEseLdllg.png)

#### GET /v1/reservation/{reservation_id}

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


### 📡 Server-Side Trace (Sampled)

| Round     | Sample Size | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|-------------|---------|---------|---------|------------|
| 1st round | 20,303      | 0.001   | 1.455   | 2.084   | 0%         |
| 2nd round | 19,850      | 0.005   | 1.578   | 2.040   | 0%         |
| 3rd round | 19,786      | 0.001   | 1.077   | 1.665   | 0%         |
| 4th round | 19,827      | 0.001   | 1.589   | 2.022   | 0%         |
| 5th round | 19,446      | 0.002   | 1.490   | 1.988   | 0%         |
| **Avg**   | **19,842**  | **0.002** | **1.438** | **1.960** | **0%** |

---

## ✅ Conclusion

### 1. Amplified Request Volume
Due to interactive queries, each request causes nearly double the traffic:

```
1,000,000 * (1 + 39 / 40) ≈ 1,975,000 requests
```

Each service instance processed roughly:

```
1,975,000 / 40 ≈ 49,375 requests
```

---

### 2. Scalability Confirmed
Compared to [16-instance test with 400,000 requests](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/16-instance-perf#-testing-result---400000-concurrent-requests)' Client-Observed Latency (Including I/O and goroutine context switch):

- **P50 latency** increased by ~33%
- **P95 latency** remained stable
- **P99 latency** decrease by ~9%

➡️ This confirms **near-linear scalability** under heavier load.

---

### 3. 📊 Span Graph Analysis

#### Reservation request distribution
From the span graph, we observe that the arrival of reservation requests:

```
POST /v1/event/{id}/reservation
```

follows a **bell curve distribution** over approximately **15 seconds**. This indicates that clients **do not send 1 million requests all at once**.

This pattern reflects **real-world user behavior**:
- Some customers send requests as soon as ticket sales open.
- Others send requests slightly later due to reaction or network delays.
- The result is a natural distribution resembling a bell curve.

However, we can consider **More aggressive load testing**: Decrease the arrival window to increase burst pressure.

#### Event Service Behavior

- The **Event Service stops processing requests after about 7 seconds**.
- This suggests the event is **sold out** within that time.
- The Event Service then **broadcasts the sold-out state** to the **Reservation Service**.
- After that, the Reservation Service begins to **immediately reject incoming reservations** without further processing.

---

### 4. Significant improvement in throughput and latency
For 1,000,000 concurrent requests.
- **50% of users**: completed in under **10 seconds**
- **99% of users**: completed in under **16 seconds**
- Event sold out within **7 seconds**

⚡ This is a significant improvement over the 5–20 minute delays reported in recent high-demand ticket sales events:
This system is **18 to 75 times faster** than Ticketmaster in those scenarios.

| Media Source | User Experience Summary |
|--------------|-------------------------|
| [Yahoo News](https://tw.news.yahoo.com/89%E8%90%AC%E4%BA%BA%E6%90%B6%E5%91%A8%E8%91%A3%E7%A5%A8-%E4%BA%94%E5%88%86%E9%90%98%E7%A7%92%E6%AE%BA-%E5%89%B5%E5%94%AE%E7%A5%A8%E7%B3%BB%E7%B5%B1%E7%B4%80%E9%8C%84-101000901.html) | Spinning for 5–10 minutes |
| [聯合新聞網](https://udn.com/news/story/7160/8310373) | Some users spun over 20 minutes |
| [NOWnews #1](https://www.nownews.com/news/6561102) | Tickets gone while users still spinning |
| [NOWnews #2](https://www.nownews.com/news/6561121) | Users still spinning after 6 minutes |

---

## 🚀 JVM Warm-Up

Java apps initially run slower due to:
- Class loading
- Bytecode interpretation

Performance increases with **JIT (Just-In-Time)** compilation.  
Warm-up ensures testing reflects the steady-state performance, not cold-start metrics.

---