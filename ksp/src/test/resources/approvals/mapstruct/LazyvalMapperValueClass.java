package test;

import jakarta.annotation.Generated;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import scenarios.edge.ValueClassFactoryPreferred;
import scenarios.edge.ValueClassFactoryPreferredJvmAccess;

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default int mapValueClassFactoryPreferredToInt(ValueClassFactoryPreferred type) {
    return ValueClassFactoryPreferredJvmAccess.score(type);
  }

  default ValueClassFactoryPreferred mapIntToValueClassFactoryPreferred(int value) {
    return ValueClassFactoryPreferredJvmAccess.of(value);
  }
}
