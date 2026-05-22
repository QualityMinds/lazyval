package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectMoreThanOneProperty {

    private final String value;
    private final String other;

    public ObjectMoreThanOneProperty(String value){
        this.value = value;
    }

    public String value(){
        return value;
    }

    public String other(){
        return other;
    }

    public static ObjectMoreThanOneProperty of(String value){
        if(value == null){
            return null;
        }
        return new ObjectMoreThanOneProperty(value);
    }
}