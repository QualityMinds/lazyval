package test;

import jakarta.annotation.Generated;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import scenarios.shared.Points;

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default Integer mapPointsToInteger(Points type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default Points mapIntegerToPoints(Integer value) {
    if (value == null) {
      return null;
    }
    return Points.of(value);
  }
}
