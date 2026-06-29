package test;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.JsonbGenerator")
@Provider
public class LazyvalJsonbContextResolver implements ContextResolver<Jsonb> {
  private final Jsonb jsonb = JsonbBuilder.create(LazyvalJsonbAdapters.config());

  @Override
  public Jsonb getContext(Class<?> type) {
    return this.jsonb;
  }
}
