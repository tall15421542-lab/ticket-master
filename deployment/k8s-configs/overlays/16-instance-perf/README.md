# 🧪 Performance Test Report

## ⚙️ Setting

* **Application Version**: [v0.0.21](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.21)

* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.22/scripts/perf/go-client/main.go)  
    ```bash
    go run main.go --host 10.140.0.30 -a 160 -env prod --http2 -n 400000 -c 8
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2-custom-16-8192`
    * vCPUs: `16`
    * Memory: `8 GB`

* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 16       | 4000m | 8 GB   |
| Reservation Service | 16       | 500m  | 2 GB   |
| Event Service       | 16       | 500m  | 2 GB   |

* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 16

* **Observability**:
  * Trace Sampling Rate: 1.25%
  * Sampler: `parentbased_traceidratio`

---

## 🚀 Testing Flow

### 1. Warm Up Services
* Services warmed up until 400,000 concurrent ticket reservation requests consistently kept CPU usage under 25% for each pod.

### 2. Spike Test Execution
* Run 400,000 concurrent reservation requests.
* Event with 160 areas, each area has 400 seats. The seats are arranged in a random continuous fashion.

### 3. Metric Collection
* Latency and trace metrics collected from both client and server perspectives.

---

## 📊 Testing Result - 400,000 Concurrent Requests

### ✅ Client Observed Latency

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 0.762   | 5.646   | 7.679   |
| 2nd round | 0.719   | 4.981   | 6.598   |
| 3rd round | 0.956   | 4.646   | 6.499   |
| 4th round | 0.635   | 6.154   | 8.630   |
| 5th round | 0.692   | 5.911   | 7.878   |
| **Avg**   | **0.753** | **5.468** | **7.457** |

---

### 🔁 Client Observed Latency (Including Retry on 503)

| Round     | P50 (s) | P95 (s) | P99 (s) |
|-----------|---------|---------|---------|
| 1st round | 8.047   | 15.465  | 17.486  |
| 2nd round | 8.223   | 15.589  | 17.300  |
| 3rd round | 7.331   | 14.591  | 16.584  |
| 4th round | 7.384   | 15.369  | 17.402  |
| 5th round | 8.708   | 16.071  | 17.866  |
| **Avg**   | **7.939** | **15.417** | **17.327** |

---

### 🖥️ Server Trace (Sampled)

| Round     | Number of Traces | P50 (s) | P90 (s) | P95 (s) | Error Rate |
|-----------|------------------|---------|---------|---------|-------------|
| 1st round | 9,773            | 0.212   | 3.673   | 5.126   | 0%          |
| 2nd round | 9,563            | 0.222   | 3.279   | 4.381   | 0%          |
| 3rd round | 9,560            | 0.314   | 2.448   | 3.703   | 0%          |
| 4th round | 9,777            | 0.184   | 3.808   | 5.603   | 0%          |
| 5th round | 9,782            | 0.173   | 3.940   | 5.525   | 0%          |
| **Avg**   | **9,691**        | **0.221** | **3.430** | **4.868** | **0%**     |

---

## 📌 Conclusion

1. The interactive queries amplify the requests. In this experiment, 400,000 requests would be equivalent to:

   ```
   400000 * (1 + 7/8) = 750,000
   ```

   Each instance would accept around `47,000` concurrent requests.

2. Compared to [1-instance-perf with 50,000 concurrent requests](https://github.com/tall15421542-lab/ticket-master/tree/v0.0.22/deployment/k8s-configs/overlays/1-instance-perf#testing-result):  
   - **P50** processing time took only **10%** of the original.  
   - **P95** took **75%**.  
   - **P99** took **93%**, with a **0% error rate**.  

   👉 This highlights strong **scalability** of the system architecture.

3. This experiment introduces a new metric: **latency including client retry** (clients retry when receiving a 5xx response).  
   - **50%** of clients saw results in under **8 seconds**.  
   - **99%** of clients saw results in under **18 seconds**.  

   🆚 This is **far better** than the reported 5-minute processing times in actual concert sales systems:

   - [Yahoo新聞：5分鐘完售！周杰倫大巨蛋演唱會15萬張票秒沒粉哀號：崩潰「轉圈圈」…記者實測待機畫面長達10來分鐘](https://tw.news.yahoo.com/18%E5%88%86%E9%90%98%E5%AE%8C%E5%94%AE-%E5%91%A8%E6%9D%B0%E5%80%AB%E5%A4%A7%E5%B7%A8%E8%9B%8B%E6%BC%94%E5%94%B1%E6%9C%8315%E8%90%AC%E5%BC%B5%E7%A5%A8%E7%A7%92%E6%B2%92-%E7%B2%89%E5%93%80%E8%99%9F-%E5%B4%A9%E6%BD%B0-%E8%BD%89%E5%9C%88%E5%9C%88-044347470.html)  
   - [聯合新聞網：大部分人都卡在「轉圈圈」，甚至有人轉超過20分鐘](https://udn.com/news/story/7160/8310373)  
   - [NOWnews：藍圈圈狂轉20分鐘15萬張門票全沒了](https://www.nownews.com/news/6561102)  
   - [NOWnews：一票粉絲抱怨網頁一直轉圈圈，「六分鐘還在轉」、「還在轉，不敢亂動」](https://www.nownews.com/news/6561121)

---

## 📝 Note: JVM Warm-Up

The JVM uses a **Just-In-Time (JIT)** compiler for runtime optimization.  
Initial executions are slower due to class loading, interpretation, and profiling.

✅ **Warming up the system ensures stable, optimized performance for accurate benchmarking**.
