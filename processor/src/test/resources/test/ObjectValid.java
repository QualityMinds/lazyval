package test;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectValid {

    private static final String FOO = new String("BAR");

    private final String value;
    private transient boolean ignoreMe;

    public ObjectValid(String value){
        this.value = value;
        this.ignoreMe = true;
    }

    public String value(){
        return value;
    }

    public boolean ignoreMe(){
        return ignoreMe;
    }

    public static String getFoo(){
        return FOO;
    }

    public static ObjectValid of(String value){
        if(value == null){
            return null;
        }
        return new ObjectValid(value);
    }
}