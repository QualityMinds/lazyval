package scenarios.evaluation.p9;

import com.qualityminds.lazyval.LazyValue;
import jakarta.validation.constraints.NotBlank;

/** {@code @NotBlank} also targets TYPE_USE, so the payload type is {@code java.lang.@NotBlank String}. */
@LazyValue
public record Mail(@NotBlank String value) {}
