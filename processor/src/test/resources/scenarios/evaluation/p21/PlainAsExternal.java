package scenarios.evaluation.p21;

import com.qualityminds.lazyval.LazyvalConfiguration;

/** Lists a nested, unannotated type of the current compilation unit as external. */
@LazyvalConfiguration(externalTypes = {Outer.Plain.class})
public interface PlainAsExternal {
}
