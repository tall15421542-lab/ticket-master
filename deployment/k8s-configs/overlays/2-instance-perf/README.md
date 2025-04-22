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
| Ticket Service      | 1        | 4000m | 8 GB   |
| Reservation Service | 1        | 500m  | 2 GB   |
| Event Service       | 1        | 500m  | 2 GB   |

* **Infra**
    * **Confluent Cloud Kafka Cluster**:
        * Type: Basic
        * Partitions for each topic: 1
* **Observability**:
    * Trace Sampling Rate: 10%
    * Sampler: `parentbased_traceidratio`

## Testing Flow
**1. Warm Up Services**
* Services warmed up until 50,000 concurrent ticket reservation requests consistently kept CPU usage under 25%.

**2. Spike Test Execution**
* Run 50,000 concurrent reservation requests.
* Event with 10 areas, each area has 400 seats. The seats are arranged in a random continuous fashion.

**3. Metric Collection**
* Latency and trace metrics collected from both client and server perspectives.

## Testing Result

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 2.254   | 5.574   | 6.022   |
| 2nd round | 2.996   | 5.430   | 6.112   |
| 3rd round | 2.611   | 5.412   | 5.994   |
| 4th round | 2.403   | 5.228   | 5.737   |
| 5th round | 2.213   | 4.921   | 5.383   |
| **Avg**   | **2.495** | **5.313** | **5.85** |

---

### 📊 **Server Trace (Sampled)**

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|-------------|
| 1st round | 2.001   | 4.539   | 5.225   | 0%          |
| 2nd round | 2.785   | 4.764   | 5.340   | 0%          |
| 3rd round | 1.980   | 4.373   | 4.901   | 0%          |
| 4th round | 2.298   | 4.616   | 5.073   | 0%          |
| 5th round | 1.985   | 4.340   | 4.760   | 0%          |
| **Avg**   | **2.21** | **4.526** | **5.186** | **0%**     |

---

## Conclusion
1. Under current configuration, the server consistently handled 50,000 concurrent reservation requests **with 95% completing in under 5.186 seconds, and 0% error rate**.
2. From the client’s perspective, **99% of users experienced a latency below 5.85 seconds**.

## Note: JVM Warm-Up
JVM is a Just-In-Time (JIT) compiler, meaning it performs runtime optimization. Initial executions tend to be slower due to class loading, interpretation, and profiling. Warming up ensures the system runs under steady-state performance before actual load testing begins.
