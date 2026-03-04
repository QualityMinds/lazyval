package test;

import test.custom.LazyvalMapper;
import org.mapstruct.MapperConfig;

@MapperConfig(
    uses = LazyvalMapper.class
)
interface DefaultMapperConfig {}