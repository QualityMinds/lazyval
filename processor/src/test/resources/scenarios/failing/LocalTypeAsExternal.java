package scenarios.failing;

import com.qualityminds.lazyval.LazyvalConfiguration;

@LazyvalConfiguration(externalTypes = {LocalTypeAsExternalReferenz.class})
public interface LocalTypeAsExternal {
}
