package lab.tall15421542.app;

import lab.tall15421542.app.domain.beans.EventBean;
import lab.tall15421542.app.domain.Schemas;
import lab.tall15421542.app.avro.event.CreateEvent;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.jackson.JacksonFeature;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

@Path("v1")
public class TicketService {
    private KafkaProducer<String, CreateEvent> producer;

    public static void main(final String[] args) {
        Properties config = new Properties();
        config.put(SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        Schemas.configureSerdes(config);

        final TicketService service = new TicketService();
        service.start("localhost:29092,localhost:39092,localhost:49092", config);
    }

    public void start(String bootstrapServers, Properties config){
        producer = startProducer(bootstrapServers, Schemas.Topics.CREATE_EVENT, config);
        startJetty(4403, this);
    }

    @GET
    @ManagedAsync
    @Path("/event/{id}")
    @Produces({MediaType.TEXT_PLAIN})
    public void getEvent(@PathParam("id") final String id,
                          @Suspended final AsyncResponse asyncResponse) {
        asyncResponse.resume(id);
    }

    @POST
    @ManagedAsync
    @Path("/event")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public void createEvent(final EventBean eventBean,
                         @Suspended final AsyncResponse asyncResponse) {
        CreateEvent req = eventBean.toAvro();
        producer.send(new ProducerRecord<String, CreateEvent>(Schemas.Topics.CREATE_EVENT.name(), req.getEventName().toString(), req));
        asyncResponse.resume(eventBean);
    }


    public static Server startJetty(final int port, final Object binding) {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        final Server jettyServer = new Server(port);
        jettyServer.setHandler(context);

        final ResourceConfig rc = new ResourceConfig();
        rc.register(binding);
        rc.register(JacksonFeature.class);

        final ServletContainer sc = new ServletContainer(rc);
        final ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");

        try {
            jettyServer.start();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return jettyServer;
    }

    public static <T> KafkaProducer startProducer(final String bootstrapServers,
                                                  final Schemas.Topic<String, T> topic,
                                                  final Properties defaultConfig) {
        final Properties producerConfig = new Properties();
        producerConfig.putAll(defaultConfig);
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, "create-event-sender");

        return new KafkaProducer<>(producerConfig,
                topic.keySerde().serializer(),
                topic.valueSerde().serializer());
    }
}
