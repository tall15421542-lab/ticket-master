### Baseline (4 vCPU Ticket Service)
#### Resource
| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 4000m  | 8 GB   |
| Reservation Service | 1        | 500m   | 2 GB   |
| Event Service       | 1        | 500m   | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.484   | 3.239   | 3.556   | 0%         |
| 2nd round | 1.355   | 3.449   | 3.889   | 0%         |
| 3rd round | 1.510   | 3.479   | 3.837   | 0%         |
| 4th round | 1.374   | 3.585   | 4.021   | 0%         |
| 5th round | 1.359   | 3.393   | 3.981   | 0%         |
| **Avg**   | **1.416** | **3.429** | **3.857** | **0%**     |

![截圖 2025-05-16 晚上8.32.59](https://hackmd.io/_uploads/S1tFlnE-xl.png)
![截圖 2025-05-16 晚上8.36.59](https://hackmd.io/_uploads/ryouWhEWxe.png)

![截圖 2025-05-16 晚上8.34.05](https://hackmd.io/_uploads/S1i6gnV-le.png)


### Ticket Service Scaled to 8 vCPU
#### Resource
| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 8000m  | 8 GB   |
| Reservation Service | 1        | 500m   | 2 GB   |
| Event Service       | 1        | 500m   | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.243   | 2.573   | 2.647   | 0%         |
| 2nd round | 1.173   | 2.546   | 2.604   | 0%         |
| 3rd round | 1.165   | 2.521   | 2.643   | 0%         |
| 4th round | 1.203   | 2.532   | 2.637   | 0%         |
| 5th round | 1.159   | 2.556   | 2.653   | 0%         |
| **Avg**   | **1.189** | **2.546** | **2.637** | **0%**     |

![截圖 2025-05-16 晚上9.10.37](https://hackmd.io/_uploads/SyCLF3NWee.png)
![截圖 2025-05-16 晚上9.12.41](https://hackmd.io/_uploads/HkoAY2V-ex.png)

### Ticket 4 vCPU, Reservation 2 vCPU, Event 1 vCPU
#### Resource
| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 4000m  | 8 GB   |
| Reservation Service | 1        | 2000m  | 4 GB   |
| Event Service       | 1        | 1000m  | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.637   | 3.727   | 4.179   | 0%         |
| 2nd round | 1.322   | 3.679   | 4.147   | 0%         |
| 3rd round | 1.245   | 3.483   | 3.965   | 0%         |
| 4th round | 1.374   | 3.647   | 4.126   | 0%         |
| 5th round | 1.289   | 3.141   | 3.567   | 0%         |
| **Avg**   | **1.373** | **3.535** | **3.997** | **0%**     |

![截圖 2025-05-16 晚上10.47.45](https://hackmd.io/_uploads/r1VQgC4-eg.png)
![截圖 2025-05-16 晚上10.48.29](https://hackmd.io/_uploads/S1bLlANZlg.png)


### Ticket 8 vCPU, Reservation 2 vCPU, Event 1 vCPU
#### Resource
| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 8000m  | 8 GB   |
| Reservation Service | 1        | 2000m  | 4 GB   |
| Event Service       | 1        | 1000m  | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.064   | 2.345   | 2.416   | 0%         |
| 2nd round | 1.188   | 2.420   | 2.492   | 0%         |
| 3rd round | 1.055   | 2.337   | 2.448   | 0%         |
| 4th round | 1.188   | 2.408   | 2.486   | 0%         |
| 5th round | 1.207   | 2.379   | 2.491   | 0%         |
| **Avg**   | **1.140** | **2.378** | **2.467** | **0%**     |

![截圖 2025-05-16 晚上9.57.20](https://hackmd.io/_uploads/SJGLNp4bxx.png)
![截圖 2025-05-16 晚上9.58.00](https://hackmd.io/_uploads/SJcd46NZxe.png)

### Ticket 16 vCPU, Reservation 2vCPU, Event 1 vCPU
#### Resource

| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 16000m | 16 GB  |
| Reservation Service | 1        | 2000m  | 4 GB   |
| Event Service       | 1        | 1000m  | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 1.274   | 2.402   | 2.476   | 0%         |
| 2nd round | 1.099   | 2.426   | 2.519   | 0%         |
| 3rd round | 0.947   | 2.276   | 2.333   | 0%         |
| 4th round | 1.093   | 2.334   | 2.437   | 0%         |
| 5th round | 1.115   | 2.302   | 2.363   | 0%         |
| **Avg**   | **1.106** | **2.348** | **2.426** | **0%**     |

![截圖 2025-05-16 晚上11.12.26](https://hackmd.io/_uploads/Bkkx8AEWlg.png)
![截圖 2025-05-16 晚上11.12.53](https://hackmd.io/_uploads/SJFbIC4-eg.png)

### Ticket 16 vCPU, Reservation 4vCPU, Event 1 vCPU
| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 16000m | 16 GB  |
| Reservation Service | 1        | 4000m  | 4 GB   |
| Event Service       | 1        | 1000m  | 2 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.936   | 1.902   | 1.970   | 0%         |
| 2nd round | 0.888   | 2.084   | 2.167   | 0%         |
| 3rd round | 0.922   | 2.006   | 2.093   | 0%         |
| 4th round | 0.895   | 2.095   | 2.171   | 0%         |
| 5th round | 0.987   | 2.101   | 2.171   | 0%         |
| **Avg**   | **0.926** | **2.038** | **2.114** | **0%**     |

![截圖 2025-05-16 晚上11.32.34](https://hackmd.io/_uploads/HJ4jcCEbxx.png)
![截圖 2025-05-16 晚上11.32.11](https://hackmd.io/_uploads/rJhKcR4-xl.png)

### Full Scaling Ticket 16 vCPU, Reservation 4vCPU, Event 4 vCPU

| Service             | Replicas | CPU    | Memory |
|---------------------|----------|--------|--------|
| Ticket Service      | 1        | 16000m | 16 GB  |
| Reservation Service | 1        | 4000m  | 4 GB   |
| Event Service       | 1        | 4000m  | 4 GB   |

| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.825   | 1.654   | 1.741   | 0%         |
| 2nd round | 0.756   | 1.759   | 1.840   | 0%         |
| 3rd round | 0.729   | 1.728   | 1.819   | 0%         |
| 4th round | 0.716   | 1.725   | 1.870   | 0%         |
| 5th round | 0.830   | 1.835   | 1.899   | 0%         |
| **Avg**   | **0.771** | **1.740** | **1.834** | **0%**     |

![截圖 2025-05-17 凌晨12.01.27](https://hackmd.io/_uploads/rk9DWkS-eg.png)
![截圖 2025-05-17 凌晨12.01.54](https://hackmd.io/_uploads/HyFtZ1B-lx.png)


#### My best record: Same Config, Different execution time
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|---------|---------|---------|------------|
| 1st round | 0.376   | 0.983   | 1.112   | 0%         |
| 2nd round | 0.514   | 1.004   | 1.156   | 0%         |
| 3rd round | 0.380   | 0.985   | 1.052   | 0%         |
| 4th round | 0.405   | 1.070   | 1.160   | 0%         |
| 5th round | 0.175   | 0.895   | 1.010   | 0%         |
| **Avg**   | **0.370** | **0.987** | **1.098** | **0%**     |


![截圖 2025-05-15 晚上8.33.50](https://hackmd.io/_uploads/Bk3N1DXWeg.png)
![截圖 2025-05-15 下午4.13.20](https://hackmd.io/_uploads/B1yNf77Zxl.png)

## Analysis
### Batching Optimization for Kafka Producer
During [previous performance testing](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf), the following Kafka configuration was applied across all producers, including the Kafka Streams application:

```properties
commit.interval.ms=50  
producer.linger.ms=50
```

With these settings, the producer sends messages either when the 50 ms linger time elapses or when the `batch.size` threshold is reached. By default, `batch.size` is set to **16 KB**.

Based on a rough estimate, each [`CreateReservation`](https://github.com/tall15421542-lab/ticket-master/blob/main/src/main/resources/avro/reservation/createReservation.avsc) Avro record is around **150 bytes**. For 50,000 reservations:

- **Total payload size** ≈ 50,000 × 150 bytes = **~7.3 MB**
- With 16 KB batches: **~458 round trips**
- If `batch.size` is increased to 2 MB: **~4 round trips**

To optimize batching and reduce network overhead, the following changes were introduced:

``` properties
commit.interval.ms=100  
compression.type=lz4  
batch.size=2097152        # 2 MB  
max.request.size=2097152  # 2 MB  
```

Larger batches, combined with LZ4 compression, improve both **network efficiency** and **compression effectiveness**, resulting in better throughput and lower latency.

#### Performance Comparison
| Configuration        | Ticket | Reservation | Event | P50 (s) | P90 (s) | P95 (s) |
|----------------------|--------|-------------|-------|---------|---------|---------|
| **Baseline.  **      | 4 vCPU | 0.5 vCPU    | 0.5 vCPU  | 2.210   | 4.526   | 5.186   |
| **2 MB batch (LZ4)** | 8 vCPU | 0.5 vCPU    | 0.5  vCPU | **1.416** | **3.429** | **3.857** |
| **Improvement**      |   –    |      –      |   –   | **-36%** | **-25%** | **-34%** |

With bigger batch size, application reduces p50 and p99 by around 35%, and reduce p90 by around 25%.

### Scaling Ticket Service Improvement

With the ticket service running on 4 vCPUs, we observed a significant latency gap when reservation responses are sent back to the ticket service. This suggests the service may be under-resourced when handling both Kafka consumption and web request load.

#### Understanding End-to-End Latency

As defined in [Confluent's latency breakdown](https://www.confluent.io/blog/configure-kafka-to-minimize-latency/#end-to-end-latency-vs-producer-and-consumer-latencies), Kafka's **end-to-end latency** includes:

1. **Produce time**
2. **Publish time**
3. **Commit time**
4. **Fetch time** (consumer side)

In our setup:
- Kafka is configured with `exactly_once_v2`
- Producers use `acks=all`

This configuration ensures the **dominant source of latency** is **fetch time**—the delay between the broker making messages available and the consumer (ticket service) processing them.

Because the ticket service acts both as:
- A **Kafka consumer**
- A **web server** handling 50,000 concurrent requests

it becomes **CPU-bound**, unable to process reservation state updates in real time.


#### Performance Comparison

| Configuration        | Ticket | Reservation | Event | P50 (s) | P90 (s) | P95 (s) |
|----------------------|--------|-------------|-------|---------|---------|---------|
| Baseline             | 4 vCPU | 0.5 vCPU    | 0.5 vCPU  | 1.416   | 3.429   | 3.857   |
| 8 vCPU               | 8 vCPU | 0.5 vCPU    | 0.5 vCPU  | **1.189** | **2.546** | **2.637** |
| **Improvement**      |   –    |      –      |   –   | **-16%** | **-26%** | **-32%** |

#### Performance Comparison with Increased Reservation/Event Resources

| Configuration         | Ticket  | Reservation | Event | P50 (s) | P90 (s) | P95 (s) |
|-----------------------|---------|-------------|-------|---------|---------|---------|
| Baseline              | 4 vCPU  | 2 vCPU      | 1 vCPU    | 1.373   |  3.535   |   3.997   |
| 8 vCPU                | 8 vCPU  | 2 vCPU      | 1 vCPU    | **1.140** (-17%) | **2.378** (-32%) | **2.467** (-39%) |
| 16 vCPU               | 16 vCPU | 2 vCPU      | 1 vCPU    | **1.106** (-20%) | **2.348** (-34%) | **2.426** (-40%) |


#### Key Observations

- **CPU Scaling Improves Latency**  
  - Median latency (P50) **drops ~16–20%**  
  - **Tail latencies (P90, P95) drop ~32–40%**

- **Ticket Service is the Bottleneck**  
  - Scaling only reservation/event services has **minimal effect** if the ticket service remains at 4 vCPUs

- **Diminishing Returns Beyond 8 vCPU**  
  - Performance gains **plateau between 8 and 16 vCPUs**
  - 8 vCPUs appear to be a **sweet spot** for the ticket service under this load

#### Conclusion

The bottleneck in this system lies in the ticket service’s ability to **consume Kafka messages and respond to web requests simultaneously**. Vertical scaling to 8 vCPUs significantly improves both average and tail latencies, while additional scaling yields diminishing returns. Efficient CPU allocation to the ticket service is therefore critical to meeting performance goals under high concurrency.

### Scaling the Reservation / Event service improvement

#### Performance Comparison

| Configuration              | Ticket  | Reservation  | Event   | P50 (s)   | P90 (s)   | P95 (s)   |
|----------------------------|---------|--------------|---------|-----------|-----------|-----------|
| **Baseline  **             | 16 vCPU | 2 vCPU       | 1 vCPU  | 1.106     |   2.348   |   2.426   |
| **Reser. scaled to 4vCPU** | 16 vCPU | 4 vCPU       | 1 vCPU  | **0.926** | **2.038** | **2.114** |
| **Improvement**             |   –    |      –       |   –     | **-16%**  | **-15%**  | **-13%**  |

| Configuration              | Ticket  | Reservation  | Event   | P50 (s)   | P90 (s)   | P95 (s)   |
|----------------------------|---------|--------------|---------|-----------|-----------|-----------|
| **Baseline**               | 16 vCPU | 4 vCPU       | 1 vCPU  | **0.926** | **2.038** | **2.114** |
| **Event. scaled to 4vCPU** | 16 vCPU | 4 vCPU       | 4 vCPU  | **0.771** | **1.740** | **1.834** |
| **Improvement**            |   –     |      –       |   –     | **-17%**  | **-15%**  | **-14%**  |

Though reservation/event service has only one task(And thus one thread processing by Kafka Streams [Threading Model](https://docs.confluent.io/platform/current/streams/architecture.html#threading-model)), adding more CPU reduces the consumer lag and reduces 15% of latency.

#### Note: GKE CPU usage metrics limitation
[GKE sampled metrics every 60 seconds](https://cloud.google.com/monitoring/api/metrics_kubernetes#kubernetes), so if I send a spike traffic and completed within 10 seconds, it would not reflect the real usage.
