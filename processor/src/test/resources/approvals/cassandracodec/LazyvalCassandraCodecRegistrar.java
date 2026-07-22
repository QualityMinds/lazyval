package test.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import com.datastax.oss.quarkus.runtime.api.config.CassandraClientConfig;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import com.datastax.oss.quarkus.runtime.internal.quarkus.CassandraClientProducer;
import io.netty.channel.EventLoopGroup;
import io.quarkus.arc.Unremovable;
import io.quarkus.netty.MainEventLoopGroup;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.concurrent.CompletionStage;
import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.cassandra.CassandraCodecGenerator")
@ApplicationScoped
@Alternative
@Priority(1)
public class LazyvalCassandraCodecRegistrar {
  private final CassandraClientProducer delegate;

  @Inject
  public LazyvalCassandraCodecRegistrar(CassandraClientProducer delegate) {
    this.delegate = delegate;
  }

  @Produces
  @ApplicationScoped
  @Unremovable
  public CompletionStage<QuarkusCqlSession> produceCodecAwareSessionStage(
      CassandraClientConfig config, @MainEventLoopGroup EventLoopGroup mainEventLoop) {
    CompletionStage<QuarkusCqlSession> stage = delegate.produceQuarkusCqlSessionStage(config, mainEventLoop);
    return stage.thenApply(session -> {
        var codecRegistry = session.getContext().getCodecRegistry();
        if (!(codecRegistry instanceof MutableCodecRegistry registry)) {
            throw new IllegalStateException(
                "CodecRegistry does not support runtime registration. Expected MutableCodecRegistry but got: " + codecRegistry.getClass().getName());
        }
        registry.register(new LazyvalCassandraCodecs.IdsProductIdCodec());
        registry.register(new LazyvalCassandraCodecs.IsbnCodec());
        registry.register(new LazyvalCassandraCodecs.OrderDateCodec());
        registry.register(new LazyvalCassandraCodecs.QuantityCodec());
        return session;
    });
  }
}
