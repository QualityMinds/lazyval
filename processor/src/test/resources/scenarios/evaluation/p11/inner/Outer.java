package scenarios.evaluation.p11.inner;

import com.qualityminds.lazyval.LazyValue;

public class Outer {

    /** Not static: every instance needs an enclosing {@code Outer}, which generated code does not have. */
    @LazyValue
    public final class Code {

        private final String value;

        public Code(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
