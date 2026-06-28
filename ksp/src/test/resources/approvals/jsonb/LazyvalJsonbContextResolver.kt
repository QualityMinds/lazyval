package test

import jakarta.`annotation`.Generated
import jakarta.json.bind.Jsonb
import jakarta.json.bind.JsonbBuilder
import jakarta.ws.rs.ext.ContextResolver
import jakarta.ws.rs.ext.Provider
import java.lang.Class

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
@Provider
public class LazyvalJsonbContextResolver : ContextResolver<Jsonb> {
  private val jsonb: Jsonb = JsonbBuilder.create(LazyvalJsonbAdapters.config())

  override fun getContext(type: Class<*>): Jsonb = jsonb
}
