package scenarios.evaluation.p21;

import com.qualityminds.lazyval.LazyvalConfiguration;

/** Lists a nested {@code @LazyValue} type of the current compilation unit as external. */
@LazyvalConfiguration(externalTypes = {Outer.Code.class})
public interface CodeAsExternal {
}
