package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue;
import java.time.LocalDate;

@LazyValue
data class OrderDate(val date: LocalDate){

    init {
        require(!date.isAfter(LocalDate.now())) { "OrderDate date cannot be in the future" }
    }
}