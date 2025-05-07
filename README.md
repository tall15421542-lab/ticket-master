# Ticket Master - MING HUNG Version

## Introduction

**Ticket Master** is a high-performance ticket selling system capable of processing **[1,000,000 concurrent reservations within 15 seconds](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/40-instance-perf)**.

The system leverages [Kafka Streams](https://kafka.apache.org/documentation/streams/), offering:

- **Exactly-once Processing Semantics**: Guarantees correctness and consistency of reservations.
- **Horizontal Scalability**: Seamlessly scales with Kafka partitioning.
- **Fault Tolerance**: Application state is backed up via changelogs in Kafka topics.

The system adopts a microservices-based stream processing architecture, consisting of:

- **Ticket Service**: Serves as the API gateway, receiving user requests.
- **Reservation Service**: A Kafka Streams processor responsible for managing reservation logic and maintaining state.
- **Event Service**: A Kafka Streams processor that handles event creation and real-time seat availability updates.


## Infrastructure
[Chart Link](https://drive.google.com/file/d/1_QCGj6DDKWuhazEUyqC6ExoQuQ7zbD1F/view?usp=sharing)
![截圖 2025-05-07 下午4.45.27](https://hackmd.io/_uploads/H1D3pq_egx.png)

## Observability

### Traces
We use [opentelemetry java agent](https://opentelemetry.io/docs/zero-code/java/agent/) with some manual instrumentation to record traces and spans.

Traces and spans are sent to Google Cloud Trace through [opentelemetry collector](https://github.com/GoogleCloudPlatform/otlp-k8s-ingest).

### Log
Logs are written to standard output and collected using [GKE's native logging support](https://cloud.google.com/kubernetes-engine/docs/concepts/about-logs).

## Deployment
### Deploy
1. Create the updated Kubernetes configuration under `overlays` directory([example](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/overlays/40-instance-perf))
2. (Optional) Overwrite [application config](https://github.com/tall15421542-lab/ticket-master/tree/main/deployment/k8s-configs/base/appConfig) under the newly created directory.
3. `make deploy -e PARTITIONS_COUNT=40 -e PERF_TYPE=40-instance-perf`
    * `PARTITIONS_COUNT`: number of partitions for topics.
    * `PERF_TYPE`: the directory name of newly created directory.
### Destroy
1. `make destroy -e PERF_TYPE=40-instance-perf`
    * `PERF_TYPE`: the directory name of newly created directory.
## Load Test

### Get gateway ip address
```bash
kubectl get gateway

NAME            CLASS                              ADDRESS         PROGRAMMED   AGE
external-http   gke-l7-regional-external-managed   35.206.193.99   True         14m
internal-http   gke-l7-rilb                        10.140.0.41     True         14m
```
We can perform load test either
1. From our local computer, send requests to `external-http` ip address.
2. Start a Google Compute Engine within the same VPC, send requests to `internal-http` ip address.

### Smoke Test
The objective of smoke test is to
1. Verify that the setup is free of basic configuration or runtime errors.
2. Allow the system to initialize and establish connections with Kafka and the Schema Registry.
```
# under scripts/perf/k6/ directory.
k6 run smoke.js -e HOST_PORT=[IP_ADDRESS] -e NUM_OF_AREAS=40
```
* `HOST_PORT`: IP address of ticket service(gateway address in kubernetes deployment).
* `NUM_OF_AREAS`: Number of areas for each event.
### Stress Test
The objective of stress test is to 
1. See the performance under high traffic over a specific duration.
2. Warm up the components for spike test.

```
# under scripts/perf/k6/ directory.
k6 run stress.js -e HOST_PORT=[IP_ADDRESS] -e NUM_OF_AREAS=40
```
* `HOST_PORT`: IP address of ticket service(gateway address in kubernetes deployment).
* `NUM_OF_AREAS`: Number of areas for each event.

### Spike Test
Spike testing is critical for ticketing systems, as traffic typically surges immediately after ticket sales begin.
```
# under scripts/perf/go-client directory.
go run main.go --host [IP_ADDRESS] -a 100 -env prod --http2 -n 250000 -c 4
```
* `--host`: IP address of ticket service(gateway address in kubernetes deployment).
* `-a`: number of areas for this event.
* `--env`: `prod` would dismiss the logging.
* `--http2`: If present, would send traffic using http2.
* `-n`: number of concurrent requests.
* `-c`: number of http clients. It aims to solve [lock contention in high concurrency scenarios](https://github.com/tall15421542-lab/ticket-master/blob/main/deployment/k8s-configs/overlays/4-instance-perf/README.md#conclusion).

## Profiling
### Java applications in the Kubernetes cluster
1. Get pod name by `kubectl get pods`.
2. Enter the pod by `kubectl exec --stdin --tty [POD_NAME]  -- /bin/bash`
3. Inside the pod:
    1. Download java jdk by 
    ```
    wget https://download.oracle.com/java/24/latest/jdk-24_linux-x64_bin.deb
    dpkg -i jdk-24_linux-x64_bin.deb
    ```
    2. Start profiling the application with the following command: 
    ```
    jcmd 1 JFR.start duration=60s filename=/tmp/recording.jfr settings=/usr/lib/jvm/jdk-24.0.1-oracle-x64/lib/jfr/profile.jfc
    ```
4. Download the recording file in the pod:
```
kubectl cp [POD_NAME]:/tmp/recording.jfr recording.jfr --retries 999
```
5. Open the JFR recording with [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)

### Go spike test client
1. Run spike test with the following options:
```
 --cpuprofile file, --cpu file      write cpu profile to file
 --memprofile file, --mem file      write memory profile to file
 --blockprofile file, --block file  write block profile to file
 --lockprofile file, --lock file    write lock profile to file
```
2. Read profiling on the web `pprof -web [PROFILE_FILE_PATH]`.

## Local Development
### prerequisite
* [Docker Desktop](https://www.docker.com/products/docker-desktop/)
* [Java](https://www.oracle.com/tw/java/technologies/downloads/)
* [Opentelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/getting-started/#setup): The following examples put the agent under `otel/` director.
### Local Infra
```
docker compose up -d
```
This would start
* [Kafka KRaft cluster](https://developer.confluent.io/learn/kraft/)
* [Schema Registry](https://github.com/confluentinc/schema-registry): RESTful interface for storing and retrieving Avro schemas.
* [Jaeger](https://www.jaegertracing.io/): Distributed tracing observability platforms.
* [Kafdrop](https://github.com/obsidiandynamics/kafdrop): Kafka Web UI for viewing Kafka topics and browsing consumer groups.
* Applications:
    * Ticket Service
    * Reservation Service
    * Event Service

### Test
```bash
./mvnw test
```
This command runs both unit and integration tests.
For **loacl load test**, check [this section](#Load-Test).

### Update Avro
1. Add or Update `.avro` files under [./src/main/resources/avro](https://github.com/tall15421542-lab/ticket-master/tree/main/src/main/resources/avro)
2. Run ``./mvnw generate-sources`` to generate the corresponding java classes.

### Opentelemetry Configurations
The following properties can configured by setting environment cariables or via the `-D` flag
* `OTEL_EXPORTER_OTLP_ENDPOINT`: The jaeger endpoint.
* `OTEL_SERVICE_NAME`: The service name including in the spans.
* `OTEL_TRACES_SAMPLER`: The sampler described [here](https://opentelemetry.io/docs/languages/java/configuration/#properties-traces).
* `OTEL_TRACES_SAMPLER_ARG`: Sampling rate described [here](https://opentelemetry.io/docs/languages/java/configuration/#properties-traces).

### Suggested JVM options
To minimize pause times and ensure low latency, we recommend using the [Z Garbage Collector](https://docs.oracle.com/en/java/javase/24/gctuning/z-garbage-collector.html).
* `-XX:+UseZGC -XX:+ZGenerational`: Configure JVM to use zgc.
* `-Xmx2G -Xms2G`: Setting the same value to reduce time for memory allocation.
* `-XX:+AlwaysPreTouch`: page in memory before the application starts.

### Build
```bash
./mvnw clean package
```
This command compile the source codes into an uber-jar using [maven-shade-plugin](https://maven.apache.org/plugins/maven-shade-plugin/index.html).

### Ticket Service
```bash
java -javaagent:./otel/opentelemetry-javaagent.jar \
-cp target/ticket-master-1.0-SNAPSHOT-shaded.jar \
lab.tall15421542.app.ticket.Service -p 8080 -d ./tmp/ticket-service/ -n 0 \
-c appConfig/client.dev.properties \
-pc appConfig/ticket-service/producer.properties \
-sc appConfig/ticket-service/stream.properties \ 
-r
```

* `-n`: The maximum of virtual threads used by Jetty. `0` means unlimited.
* `-p`: The HTTP port of the ticket service.
* `-d`: Directory path for stroing state.
* `-c`: Config file path for kafka and schema registry connectivity properties.
* `-pc`: Config file path for [Kafka producer properties](https://docs.confluent.io/platform/current/installation/configuration/producer-configs.html).
* `-sc`: Config file path for [Kafka Streams properties](https://docs.confluent.io/platform/current/installation/configuration/streams-configs.html).
* `-r`: If present, enable the request log.
* `-a`: Specify the number of [Jetty acceptors](https://jetty.org/docs/jetty/12/programming-guide/server/http.html#connector-acceptors).
* `-s`: Specify the number of [Jetty selectors](https://jetty.org/docs/jetty/12/programming-guide/server/http.html#connector-selectors).

### Reservation Service
```
java -javaagent:./otel/opentelemetry-javaagent.jar \
-cp target/ticket-master-1.0-SNAPSHOT-shaded.jar \
lab.tall15421542.app.reservation.Service \
-c appConfig/client.dev.properties \
-sc appConfig/reservation-service/stream.properties \
-d ./tmp/reservation-service
```
* `-c`: Config file path for kafka and schema registry connectivity properties.
* `-sc`: Config file path for [Kafka Streams properties](https://docs.confluent.io/platform/current/installation/configuration/streams-configs.html).
* `-d`: Directory path for stroing state.
### Event Service
```
java -javaagent:./otel/opentelemetry-javaagent.jar \
-cp target/ticket-master-1.0-SNAPSHOT-shaded.jar \
lab.tall15421542.app.event.Service \
-c appConfig/client.dev.properties \
-sc appConfig/event-service/stream.properties \
-d ./tmp/event-service
```
* `-c`: Config file path for kafka and schema registry connectivity properties.
* `-sc`: Config file path for [Kafka Streams properties](https://docs.confluent.io/platform/current/installation/configuration/streams-configs.html).
* `-d`: Directory path for stroing state.
    
### Tracing - Jaeger
open http://localhost:16686/
### Kafdrop
open http://localhost:9000/

