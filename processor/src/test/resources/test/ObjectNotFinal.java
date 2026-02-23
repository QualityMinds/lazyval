package test;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public class ObjectNotFinal {

    private final String value;

    public ObjectNotFinal(String value){
        this.value = value;
    }

    public String value(){
        return value;
    }

    public static ObjectNotFinal of(String value){
        if(value == null){
            return null;
        }
        return new ObjectNotFinal(value);
    }
}