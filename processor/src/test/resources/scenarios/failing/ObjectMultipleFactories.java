package test;

import de.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectMultipleFactories {

    private final String value;

    public ObjectMultipleFactories(String value){
        this.value = value;
    }

    public String value(){
        return value;
    }

    public static ObjectMultipleFactories of(String value){
        if(value == null){
            return null;
        }
        return new ObjectMultipleFactories(value);
    }

    public static ObjectMultipleFactories accidental(String value){
        if(value == null){
            return null;
        }
        return new ObjectMultipleFactories(value);
    }
}