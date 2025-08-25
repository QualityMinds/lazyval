package test;

import de.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectValidInteger {

    private static final String FOO = new String("BAR");

    private final Integer value;
    private transient boolean ignoreMe;

    private ObjectValidInteger(Integer value){
        this.value = value;
        this.ignoreMe = true;
    }

    public Integer value(){
        return value;
    }

    public boolean ignoreMe(){
        return ignoreMe;
    }

    public static String getFoo(){
        return FOO;
    }

    public static ObjectValidInteger of(Integer value){
        if(value == null){
            return null;
        }
        return new ObjectValidInteger(value);
    }
}