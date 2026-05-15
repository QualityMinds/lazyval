package scenarios.java;

import com.qualityminds.lazyval.LazyValue;
import java.time.LocalDate;

@LazyValue
public record Birthday(LocalDate value) {
    public Birthday {
        if (value == null) {
            throw new IllegalArgumentException("Birthday must not be null");
        }
        if(value.isAfter(LocalDate.now())){
            throw new IllegalArgumentException("Birthday must not be in the future");
        }
    }
}
