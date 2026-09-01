package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

import jakarta.persistence.Transient;

/**
 * The counterpart to {@code scenarios/jpa/JpaTransientRecordComponent}, with the single-argument
 * constructor left out. A record cannot hold derived state outside its header, so marking a component
 * transient widens the canonical constructor past the payload — and nothing else here narrows it
 * again. Generated code reconstructs from the payload alone, so there is no call for it to make.
 * <p>
 * This is the rule documented at {@code rules.adoc#payload}: a transient record component obliges the
 * author to supply a single-argument constructor or a factory method.
 */
@LazyValue
public record RecordTransientWithoutFactory(String value, @Transient int derivedLength) {
}
