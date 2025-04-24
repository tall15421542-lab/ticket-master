# Perfomance Test Report

## Setting
* **Application Version**: [v0.0.21](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.21)
* **Spike Test Client**:
    * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.21/scripts/perf/go-client/main.go)
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
* Services warmed up until 66,666 concurrent ticket reservation requests consistently kept CPU usage under 25% for each pods.

**2. Spike Test Execution**
* Run 
    * 66,666 concurrent reservation requests - test 50,000 equivalent requests for each pod.
    * 100,000 concurrent reservation requests - test scalability.
* Event with 20 areas, each area has 400 seats. The seats are arranged in a random continuous fashion.

**3. Metric Collection**
* Latency and trace metrics collected from both client and server perspectives.

## Testing Result - 66666 concurrent requests

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.420   | 6.395   | 7.936   |
| 2nd round | 0.393   | 6.904   | 8.076   |
| 3rd round | 0.296   | 4.155   | 5.114   |
| 4th round | 0.438   | 5.866   | 6.943   |
| 5th round | 0.230   | 3.837   | 5.187   |
| **Avg**   | **0.355** | **5.431** | **6.651** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 5,063            | 0.071   | 4.453   | 5.950   | 0%          |
| 2nd round | 5,127            | 0.062   | 4.587   | 6.201   | 0%          |
| 3rd round | 4,973            | 0.096   | 3.578   | 4.238   | 0%          |
| 4th round | 4,789            | 0.063   | 4.001   | 5.523   | 0%          |
| 5th round | 4,961            | 0.063   | 3.232   | 3.831   | 0%          |
| **Avg**   | **5,002.6**       | **0.071** | **3.970** | **5.149** | **0%** |

---

## Conclusion
1. **Server processing time improved dramatically over [v0.0.19](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/2-instance-perf)**: p50 is 650% faster, p90/p95 are 40–50% faster, and error rate is now 0%.
2. **[Jetty HTTP/2 client](https://github.com/tall15421542-lab/ticket-master/commit/5b9629dbdd726fd20dce9d492bbb5e56ae1a9461)**: Only 21 socket I/O events >20ms in 1 minute, with p99 duration <100ms, indicating efficient client-side networking.[(JFR records)](https://github.com/tall15421542-lab/ticket-master/blob/main/deployment/k8s-configs/overlays/2-instance-perf-with-jetty-client/recording.2-instance-perf-with-jetty-client.jfr)

## Testing Result - 100,000 concurrent requests

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.227   | 6.822   | 8.925   |
| 2nd round | 0.178   | 5.854   | 7.615   |
| 3rd round | 0.245   | 6.025   | 7.569   |
| 4th round | 0.204   | 6.433   | 7.691   |
| 5th round | 0.187   | 6.064   | 7.843   |
| **Avg**   | **0.208** | **6.240** | **7.929** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 7,386            | 0.032   | 4.682   | 6.287   | 0%          |
| 2nd round | 7,534            | 0.026   | 3.930   | 5.460   | 0%          |
| 3rd round | 7,380            | 0.037   | 4.426   | 5.966   | 0%          |
| 4th round | 7,458            | 0.033   | 4.858   | 6.163   | 0%          |
| 5th round | 7,621            | 0.026   | 4.049   | 5.667   | 0%          |
| **Avg**   | **7,475.8**       | **0.031** | **4.389** | **5.909** | **0%** |

## Conclusion
* Compared to [50,000 requests on a single pod](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf), 100,000 requests on two pods is only 13% slower at p95, with p90 unchanged and p50 613% faster.

* **Scalability**: Doubling hardware resources enables the system to handle double the requests with similar processing times for 90% of cases, confirming good horizontal scalability.

## Note: JVM Warm-Up
JVM is a Just-In-Time (JIT) compiler, meaning it performs runtime optimization. Initial executions tend to be slower due to class loading, interpretation, and profiling. Warming up ensures the system runs under steady-state performance before actual load testing begins.
