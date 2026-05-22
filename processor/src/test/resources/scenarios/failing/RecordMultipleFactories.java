package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public record RecordMultipleFactories(String value) {

    public static RecordMultipleFactories of(String value){
        if(value == null){
            return null;
        }
        return new RecordMultipleFactories(value);
    }

    public static RecordMultipleFactories accidental(String value){
        if(value == null){
            return null;
        }
        return new RecordMultipleFactories(value);
    }
}