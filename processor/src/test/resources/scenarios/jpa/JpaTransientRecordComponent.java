package scenarios.jpa;

import com.qualityminds.lazyval.LazyValue;

import jakarta.persistence.Transient;

/**
 * Record counterpart to {@code JpaTransientField}/{@code JpaTransientGetter}: the derived component
 * carries JPA's {@code @Transient}. The annotation has no RECORD_COMPONENT target, so javac
 * propagates it onto the generated field and accessor (JLS 8.10.3) — the validator has to look
 * there, because {@code getRecordComponents()} still reports both components.
 * <p>
 * Records cannot hold derived state outside the header, so the canonical constructor takes two
 * arguments; the single-argument constructor is the route generated code reconstructs the value by.
 */
@LazyValue
public record JpaTransientRecordComponent(String value, @Transient int derivedLength) {

    public JpaTransientRecordComponent(String value) {
        this(value, value.length());
    }
}
