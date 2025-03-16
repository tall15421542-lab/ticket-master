package lab.tall15421542.app.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;

public class ObjectMapperWithTimeModule implements ContextResolver<ObjectMapper> {
    final ObjectMapper jsonMapper;

    public ObjectMapperWithTimeModule() {
        jsonMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }
    @Override
    public ObjectMapper getContext(final Class<?> type) {
       return jsonMapper;
    }
}
