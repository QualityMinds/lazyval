package test;

import test.custom.LazyvalMapper;
import org.mapstruct.MapperConfig;

// tag::docu[]
@MapperConfig(
        uses = LazyvalMapper.class // <1>
)
public interface DefaultMapperConfig {
}
// end::docu[]
