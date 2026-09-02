package test;

import jakarta.annotation.Generated;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import scenarios.edge.FactoryOnNamedCompanion;
import scenarios.edge.FactoryWithRenamedJvmName;
import scenarios.edge.FactoryWithoutJvmStatic;
import scenarios.edge.InternalFactoryOnCompanion;
import scenarios.edge.InternalFactoryWithJvmName;
import scenarios.edge.PropertyWithRenamedJvmName;

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default String mapFactoryOnNamedCompanionToString(FactoryOnNamedCompanion type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default FactoryOnNamedCompanion mapStringToFactoryOnNamedCompanion(String value) {
    if (value == null) {
      return null;
    }
    return FactoryOnNamedCompanion.Factory.of(value);
  }

  default String mapFactoryWithRenamedJvmNameToString(FactoryWithRenamedJvmName type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default FactoryWithRenamedJvmName mapStringToFactoryWithRenamedJvmName(String value) {
    if (value == null) {
      return null;
    }
    return FactoryWithRenamedJvmName.create(value);
  }

  default String mapFactoryWithoutJvmStaticToString(FactoryWithoutJvmStatic type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default FactoryWithoutJvmStatic mapStringToFactoryWithoutJvmStatic(String value) {
    if (value == null) {
      return null;
    }
    return FactoryWithoutJvmStatic.Companion.of(value);
  }

  default String mapInternalFactoryOnCompanionToString(InternalFactoryOnCompanion type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default InternalFactoryOnCompanion mapStringToInternalFactoryOnCompanion(String value) {
    if (value == null) {
      return null;
    }
    return InternalFactoryOnCompanion.Companion.of(value);
  }

  default String mapInternalFactoryWithJvmNameToString(InternalFactoryWithJvmName type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default InternalFactoryWithJvmName mapStringToInternalFactoryWithJvmName(String value) {
    if (value == null) {
      return null;
    }
    return InternalFactoryWithJvmName.of(value);
  }

  default String mapPropertyWithRenamedJvmNameToString(PropertyWithRenamedJvmName type) {
    if (type == null) {
      return null;
    }
    return type.payload();
  }

  default PropertyWithRenamedJvmName mapStringToPropertyWithRenamedJvmName(String value) {
    if (value == null) {
      return null;
    }
    return new PropertyWithRenamedJvmName(value);
  }
}
