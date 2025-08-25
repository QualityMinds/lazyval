package test;

import de.qualityminds.lazyval.LazyValue;

@LazyValue
public record RecordValidInteger(int value) {

    public static RecordValidInteger of(Integer value){
        if(value == null){
            return null;
        }
        return new RecordValidInteger(value);
    }
}