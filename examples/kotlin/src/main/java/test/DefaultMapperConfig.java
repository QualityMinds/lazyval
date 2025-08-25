package test;

import org.mapstruct.MapperConfig;

@MapperConfig(
    uses = LazyvalMapper.class
)
interface DefaultMapperConfig {}