# ticket-master

## Usage
### Compile
``mvn install``

### Run
``mvn exec:java -Dexec.mainClass="lab.tall15421542.app.TicketService"``

### Start Infra
``docker-compose up -d``

### Check Topic Message
```
./kafka-avro-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic createEvent \
  --from-beginning \
  --property schema.registry.url=http://localhost:8081
```
