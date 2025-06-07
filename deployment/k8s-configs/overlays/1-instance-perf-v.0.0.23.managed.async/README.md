# Performance Report

## ⚙️ Setting
* **Application Version**: [v.0.0.23.managed.async](https://github.com/tall15421542-lab/ticket-master/tree/v.0.0.23.managed.async) with [configuration](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/1-instance-perf-v.0.0.23.managed.async/appConfig)
* **Services**:

| Service             | Replicas | CPU   | Memory |
|---------------------|----------|-------|--------|
| Ticket Service      | 1        | 8000m | 8 GB   |
| Reservation Service | 1        | 4000m | 4 GB   |
| Event Service       | 1        | 4000m | 4 GB   |
* **Spike Test Client**:
  * Code: [main.go](https://github.com/tall15421542-lab/ticket-master/blob/v0.0.23/scripts/perf/go-client/main.go)  
    ```bash
    time go run main.go --host 10.140.0.9 -a 80 -env prod --http2 -n 50000
    ```
  * **Google Compute Engine**:
    * Machine Type: `n2`
    * vCPUs: `16`
    * Memory: `8 GB`
* **Infra**
  * **Confluent Cloud Kafka Cluster**:
    * Type: Basic
    * Partitions for each topic: 1

* **Observability**:
  * Trace Sampling Rate: 10%
  * Sampler: `parentbased_traceidratio`

## 50000 Reservations
### v0.0.23 Baseline
### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.170   | 2.335   | 2.566   | 0%         | 7                  |
| 2nd round | 1.074   | 2.154   | 2.505   | 0%         | 6                  |
| 3rd round | 1.107   | 2.233   | 2.467   | 0%         | 7                  |
| 4th round | 1.154   | 2.045   | 2.292   | 0%         | 6                  |
| 5th round | 1.174   | 2.349   | 2.586   | 0%         | 7                  |
| **Avg**   | **1.136** | **2.223** | **2.483** | **0%**     | **6.6**              |

![截圖 2025-06-07 下午6.38.53](https://hackmd.io/_uploads/H15pUqW7ee.png)
![截圖 2025-06-07 下午6.39.37](https://hackmd.io/_uploads/HJulPqZ7xg.png)
![截圖 2025-06-07 下午6.40.46](https://hackmd.io/_uploads/BJ64DqZ7eg.png)


### With ManagedAsync
#### Server-Side Trace (Sampled)
| Round     | P50 (s) | P90 (s) | P95 (s) | Error Rate | Completion Time(s) |
|-----------|---------|---------|---------|------------|--------------------|
| 1st round | 1.904   | 4.117   | 4.560   | 0%         | 10                 |
| 2nd round | 1.895   | 4.308   | 4.669   | 0%         | 9                  |
| 3rd round | 1.755   | 3.954   | 4.393   | 0%         | 9                  |
| 4th round | 1.638   | 3.784   | 4.030   | 0%         | 9                  |
| 5th round | 1.655   | 3.684   | 4.212   | 0%         | 9                  |
| **Avg**   | **1.769** | **3.969** | **4.373** | **0%**     | **9.2**              |

[trace](https://console.cloud.google.com/traces/explorer;query=%7B%22plotType%22:%22HEATMAP%22,%22pointConnectionMethod%22:%22GAP_DETECTION%22,%22targetAxis%22:%22Y1%22,%22traceQuery%22:%7B%22resourceContainer%22:%22projects%2Fticket-master-tall15421542%2Flocations%2Fglobal%2FtraceScopes%2F_Default%22,%22spanDataValue%22:%22SPAN_DURATION%22,%22spanFilters%22:%7B%22attributes%22:%5B%5D,%22displayNames%22:%5B%22GET%20%2Fv1%2Freservation%2F%7Breservation_id%7D%22,%22POST%20%2Fv1%2Fevent%2F%7Bid%7D%2Freservation%22%5D,%22isRootSpan%22:false,%22kinds%22:%5B%5D,%22maxDuration%22:%22%22,%22minDuration%22:%22%22,%22services%22:%5B%5D,%22status%22:%5B%5D%7D%7D%7D;startTime=2025-06-06T13:09:00.543Z;endTime=2025-06-06T13:10:00.543Z?referrer=search&inv=1&invt=AbzZAQ&project=ticket-master-tall15421542)
![截圖 2025-06-07 下午3.24.17](https://hackmd.io/_uploads/H1WEtPWQxl.png)
![截圖 2025-06-07 下午3.36.19](https://hackmd.io/_uploads/r1WW2vWQxe.png)
![截圖 2025-06-07 下午3.36.47](https://hackmd.io/_uploads/r1gnGnPbXxx.png)

## Conclusion
With virtual thread 
* the RPS improve 40% RPS(From 5434 to 7575)
* P95 reduced 44% latency
* P90 reduced 44% latency
* p50 reduced 36% latenct 
