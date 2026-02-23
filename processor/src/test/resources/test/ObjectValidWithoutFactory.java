package test;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectValidWithoutFactory {

    private static final String FOO = new String("BAR");

    private final String value;

    public ObjectValidWithoutFactory(String value){
        this.value = value;
    }

    public String value(){
        return value;
    }

    public static String getFoo(){
        return FOO;
    }
}