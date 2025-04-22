# Perfomance Test Report

## Setting
* **Application Version**: [v0.0.19](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.19)
* **Spike Test Client**:
    * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.19/scripts/perf/go-client/main.go)
    * **Google Compute Engine**:
        * Machine Type: `n2-highcpu-4`
        * vCPUs: `4`
        * Memory: `4GB`
* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 2        | 4000m | 8 GB   |
| Reservation Service | 2        | 500m  | 2 GB   |
| Event Service       | 2        | 500m  | 2 GB   |

* **Infra**
    * **Confluent Cloud Kafka Cluster**:
        * Type: Basic
        * Partitions for each topic: 2
* **Observability**:
    * Trace Sampling Rate: 5%
    * Sampler: `parentbased_traceidratio`

## Testing Flow
**1. Warm Up Services**
* Services warmed up until 66,666 concurrent ticket reservation requests consistently kept CPU usage under 25% for each pod.

**2. Spike Test Execution**
* Run 66,666 concurrent reservation requests.
* Event with 20 areas, each area has 400 seats. The seats are arranged in a random, continuous fashion.

**3. Metric Collection**
* Latency and trace metrics collected from both client and server perspectives.

## Testing Result

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.427   | 8.660   | 9.808   |
| 2nd round | 0.890   | 8.010   | 9.434   |
| 3rd round | 0.827   | 8.408   | 9.805   |
| 4th round | 1.412   | 6.209   | 7.607   |
| 5th round | 0.693   | 8.192   | 9.625   |
| **Avg**   | **0.850** | **7.496** | **9.256** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 5,020            | 0.117   | 6.811   | 8.420   | 0.08%       |
| 2nd round | 5,114            | 0.567   | 6.590   | 8.014   | 0%          |
| 3rd round | 5,091            | 0.547   | 6.747   | 8.584   | 1.55%       |
| 4th round | 4,890            | 0.945   | 3.675   | 5.419   | 0%          |
| 5th round | 4,912            | 0.497   | 6.671   | 8.037   | 0.65%       |
| **Avg**   | **5,005.4**       | **0.535** | **6.099** | **7.295** | **0.456%** |

---

## Pod Metric chart
### Ticket Service
<img width="892" alt="截圖 2025-04-22 下午2 49 15" src="https://github.com/user-attachments/assets/fe1c3dea-ce52-4416-9c7d-6f67c9efb390" />

### Reservation Service
<img width="1094" alt="截圖 2025-04-22 下午2 47 12" src="https://github.com/user-attachments/assets/52232243-4daa-4f64-8580-d4c8d74bca52" />

### Event Service
<img width="1092" alt="截圖 2025-04-22 下午2 44 42" src="https://github.com/user-attachments/assets/68b2c457-9cf0-4921-ab25-1d3bf3992165" />


## Conclusion
1. Although only 66,666 concurrent requests were sent, we observed approximately 100,000 trace entries (Traces ÷ sampling rate). This is due to interactive queries: incoming requests are randomly routed to pods, and if the selected pod doesn't own the data partition, an additional HTTP request is triggered to reach the correct one. In the case of two servers, this pattern would increase 50% traffic, because 50% of requests require additional routing.

<img width="1820" alt="截圖 2025-04-22 晚上9 53 11" src="https://github.com/user-attachments/assets/bb21ada7-a62a-4a1e-8a79-f0c54d4fc3fb" />

2. From the pod metrics, it’s clear that the Reservation and Event services do not have high CPU/memory usage. The ticket service handles the high concurrency directly, which is the performance bottleneck.

<img width="1819" alt="截圖 2025-04-22 晚上10 01 46" src="https://github.com/user-attachments/assets/8eb1aac4-14d3-4a20-858c-64b420f39e0a" />

3. Compared to [single instance performance test](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf), the P95 and P99 latencies increased by ~35–40%. This is due to increased network I/O from interactive queries. In the [current version](https://github.com/tall15421542-lab/ticket-master/blob/b207781447ddb2cce815f777ab7ab09b7cd29165/src/main/java/lab/tall15421542/app/ticket/Service.java#L81), these HTTP requests use Jersey with HTTP/1.1, which lacks connection pooling and keep-alive support.

4. The longer tail latency also correlates with a higher error rate (0.456%), caused by server-side 503 errors due to request timeouts. The server enforces a 10-second timeout, after which it cancels the request if processing hasn't completed.

## Possible Optimization
* Replace Jersey client with a high-performance HTTP client that supports HTTP/2 and connection pooling to better handle high concurrent outbound requests in the Ticket Service.

## Note: JVM Warm-Up
JVM is a Just-In-Time (JIT) compiler, meaning it performs runtime optimization. Initial executions tend to be slower due to class loading, interpretation, and profiling. Warming up ensures the system runs under steady-state performance before actual load testing begins.
