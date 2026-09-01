package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

// TODO move this file as a subtype of LocalTypeAsExternal once subtypes are working

@LazyValue
public record LocalTypeAsExternalReferenz(String value) {
}
