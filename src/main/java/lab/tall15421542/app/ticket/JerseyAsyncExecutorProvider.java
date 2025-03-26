package lab.tall15421542.app.ticket;

import org.glassfish.jersey.server.ManagedAsyncExecutor;
import org.glassfish.jersey.spi.ExecutorServiceProvider;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@ManagedAsyncExecutor
public class JerseyAsyncExecutorProvider implements ExecutorServiceProvider {
    private final Supplier<ExecutorService> executorServiceSupplier;

    public JerseyAsyncExecutorProvider(Supplier<ExecutorService> supplier){
        this.executorServiceSupplier = supplier;
    }

    @Override
    public ExecutorService getExecutorService(){
        return executorServiceSupplier.get();
    }

    @Override
    public void dispose(ExecutorService executorService){
        executorService.close();
    }
}
