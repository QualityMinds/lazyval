package test.boundary.persistence.cassandra

import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry
import com.datastax.oss.quarkus.runtime.`internal`.quarkus.CassandraClientProducer
import com.datastax.oss.quarkus.runtime.api.config.CassandraClientConfig
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession
import io.netty.channel.EventLoopGroup
import io.quarkus.arc.Unremovable
import io.quarkus.netty.MainEventLoopGroup
import jakarta.`annotation`.Generated
import jakarta.`annotation`.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import java.util.concurrent.CompletionStage

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.cassandra.CassandraCodecGenerator")
@ApplicationScoped
@Alternative
@Priority(value = 1)
public class LazyvalCassandraCodecRegistrar @Inject constructor(
  private val `delegate`: CassandraClientProducer,
) {
  @Produces
  @ApplicationScoped
  @Unremovable
  public fun produceCodecAwareSessionStage(config: CassandraClientConfig, @MainEventLoopGroup mainEventLoop: EventLoopGroup): CompletionStage<QuarkusCqlSession> {
    val stage = delegate.produceQuarkusCqlSessionStage(config, mainEventLoop)
    return stage.thenApply { session ->
        val registry = session.context.codecRegistry as? MutableCodecRegistry
            ?: throw IllegalStateException(
                "CodecRegistry does not support runtime registration. Expected MutableCodecRegistry but got: " + session.context.codecRegistry::class.java.name
            )
        registry.register(LazyvalCassandraCodecs.IdsProductIdCodec())
        registry.register(LazyvalCassandraCodecs.IsbnCodec())
        registry.register(LazyvalCassandraCodecs.NullableQuantityCodec())
        registry.register(LazyvalCassandraCodecs.OrderDateCodec())
        registry.register(LazyvalCassandraCodecs.QuantityCodec())
        session
    }
  }
}
