package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * An enum has a fixed set of instances rather than one per payload, so there is nothing to rebuild. Lazyval
 * used to skip it without a word; it is refused before any other rule is asked.
 */
@LazyValue
public enum EnumDomainPrimitive {
    ONE,
    TWO
}
