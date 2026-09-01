package scenarios.springdata;

import com.qualityminds.lazyval.LazyValue;

import org.springframework.data.annotation.Transient;

/**
 * Record counterpart to {@code SpringDataTransientField}/{@code SpringDataTransientGetter}: the
 * derived component carries Spring Data's {@code @Transient}. The annotation has no
 * RECORD_COMPONENT target, so javac propagates it onto the generated field and accessor
 * (JLS 8.10.3) — the validator has to look there, because {@code getRecordComponents()} still
 * reports both components.
 */
@LazyValue
public record SpringDataTransientRecordComponent(String value, @Transient int derivedLength) {

    public SpringDataTransientRecordComponent(String value) {
        this(value, value.length());
    }
}
