package scenarios.java;

import com.qualityminds.lazyval.LazyValue;
import java.time.LocalDate;

@LazyValue
public record OrderDate(LocalDate value) {
    public OrderDate {
        if (value == null) {
            throw new IllegalArgumentException("OrderDate must not be null");
        }
        if(value.isAfter(LocalDate.now())){
            throw new IllegalArgumentException("OrderDate must not be in the future");
        }
    }
}
