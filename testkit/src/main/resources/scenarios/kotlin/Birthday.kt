package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue;
import java.time.LocalDate;

@LazyValue
data class Birthday(val date: LocalDate){

    init {
        require(!date.isAfter(LocalDate.now())) { "Birthday date cannot be in the future" }
    }
}