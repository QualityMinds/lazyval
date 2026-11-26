package test;

import javax.annotation.processing.Generated;
import org.mapstruct.Mapper;
import scenarios.shared.Points;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default Integer mapPointsToInteger(Points type) {
    if(type == null) {
      return null;
    }
    return type.getValue();
  }

  default Points mapIntegerToPoints(Integer value) {
    if(value == null) {
      return null;
    }
    return Points.of(value);
  }
}
