package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectMissingValueAccessor {

    private final String value;

    public ObjectMissingValueAccessor(String value){
        this.value = value;
    }

    public static ObjectMissingValueAccessor of(String value){
        if(value == null){
            return null;
        }
        return new ObjectMissingValueAccessor(value);
    }
}