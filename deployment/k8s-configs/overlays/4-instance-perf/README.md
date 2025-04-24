# Performance Test Report

## Setting
* **Application Version**: [v0.0.21](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.21)
* **Spike Test Client**:
    * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.21/scripts/perf/go-client/main.go)
    * **Google Compute Engine**:
        * Machine Type: `n2-highcpu-4`
        * vCPUs: `8`
        * Memory: `8GB`
* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 4        | 4000m | 8 GB   |
| Reservation Service | 4        | 500m  | 2 GB   |
| Event Service       | 4        | 500m  | 2 GB   |

* **Infra**
    * **Confluent Cloud Kafka Cluster**:
        * Type: Basic
        * Partitions for each topic: 4
* **Observability**:
    * Trace Sampling Rate: 5%
    * Sampler: `parentbased_traceidratio`

## Testing Flow
**1. Warm Up Services**
* Services warmed up until 200,000 concurrent ticket reservation requests consistently kept CPU usage under 25% for each pods.

**2. Spike Test Execution**
* Run 
    * 200,000 concurrent reservation requests.
    * 250,000 concurrent reservation requests.
    * 350,000 concurrent reservation requests.
* Event with 40 areas, each area has 400 seats. The seats are arranged in a random, continuous fashion.

**3. Metric Collection**
* Latency and trace metrics collected from both client and server perspectives.

## Testing Result - 200,000 concurrent requests

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.061   | 1.983   | 5.829   |
| 2nd round | 0.092   | 2.126   | 2.824   |
| 3rd round | 0.051   | 2.794   | 6.807   |
| 4th round | 0.049   | 2.362   | 3.856   |
| 5th round | 0.066   | 2.077   | 2.956   |
| **Avg**   | **0.064** | **2.268** | **4.254** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 17,597           | 0.016   | 1.520   | 1.924   | 0%          |
| 2nd round | 17,390           | 0.027   | 1.334   | 1.992   | 0%          |
| 3rd round | 17,616           | 0.008   | 1.562   | 2.714   | 0%          |
| 4th round | 17,454           | 0.012   | 1.767   | 2.372   | 0%          |
| 5th round | 17,800           | 0.019   | 1.566   | 2.024   | 0%          |
| **Avg**   | **17,571**       | **0.016** | **1.550** | **2.205** | **0%** |

---

## Testing Result - 250,000 concurrent requests

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.061   | 2.904   | 4.441   |
| 2nd round | 0.050   | 2.710   | 4.018   |
| 3rd round | 0.051   | 2.643   | 3.438   |
| 4th round | 0.061   | 2.106   | 3.772   |
| 5th round | 0.076   | 2.875   | 5.108   |
| **Avg**   | **0.060** | **2.648** | **4.155** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 22,099           | 0.015   | 2.008   | 2.878   | 0%          |
| 2nd round | 21,958           | 0.014   | 1.782   | 2.715   | 0%          |
| 3rd round | 21,979           | 0.011   | 1.780   | 2.602   | 0%          |
| 4th round | 21,900           | 0.015   | 1.460   | 2.042   | 0%          |
| 5th round | 21,751           | 0.017   | 1.730   | 2.840   | 0%          |
| **Avg**   | **21,937**       | **0.014** | **1.752** | **2.615** | **0%** |

---

## Testing Result - 350,000 concurrent requests

### 📊 **Client Observed Latency**

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.019   | 3.638   | 5.539   |
| 2nd round | 0.026   | 3.525   | 4.647   |
| 3rd round | 0.041   | 3.430   | 6.481   |
| 4th round | 0.033   | 4.212   | 8.142   |
| 5th round | 0.029   | 2.846   | 4.976   |
| **Avg**   | **0.030** | **3.530** | **5.957** |

---

### 📊 **Server Trace (Sampled)**

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 30,543           | 0.004   | 2.707   | 3.674   | 0%          |
| 2nd round | 30,558           | 0.005   | 2.623   | 3.507   | 0%          |
| 3rd round | 30,706           | 0.008   | 2.545   | 3.241   | 0%          |
| 4th round | 30,529           | 0.005   | 2.074   | 4.504   | 0%          |
| 5th round | 31,002           | 0.005   | 2.405   | 3.639   | 0%          |
| **Avg**   | **30,668**       | **0.005** | **2.471** | **3.713** | **0%** |


## Note: JVM Warm-Up
JVM is a Just-In-Time (JIT) compiler, meaning it performs runtime optimization. Initial executions tend to be slower due to class loading, interpretation, and profiling. Warming up ensures the system runs under steady-state performance before actual load testing begins.
