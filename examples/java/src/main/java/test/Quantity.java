package test;

import de.qualityminds.lazyval.LazyValue;
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
