package test;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public record RecordValid(String value) {

    public static RecordValid of(String value){
        if(value == null){
            return null;
        }
        return new RecordValid(value);
    }
}