package lab.tall15421542.app;

import lab.tall15421542.app.domain.beans.EventBean;

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

@Path("v1")
public class TicketService {

    public static void main(final String[] args) {
        final TicketService service = new TicketService();
        service.start();
    }

    public void start(){
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
}
