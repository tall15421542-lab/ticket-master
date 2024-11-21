# ticket-master

## Usage
### Compile
``mvn install``

### Run
``mvn exec:java -Dexec.mainClass="lab.tall15421542.app.TicketService"``

### Start Infra
``docker-compose up -d``

### Check Topic Message
#### Install kafka-avro-console-consumer
```
curl -O https://packages.confluent.io/archive/7.7/confluent-community-7.7.1.tar.gz
tar xzf confluent-community-7.7.1.tar.gz
```

#### Check Topic Message command
```
./kafka-avro-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic createEvent \
  --from-beginning \
  --property schema.registry.url=http://localhost:8081
```
