package test;

import com.qualityminds.lazyval.LazyValue;
// tag::docu[]
@LazyValue()
public record Quantity(int value) {

    public Quantity {
        if(value <= 0){
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }
}
// end::docu[]
