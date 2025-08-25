package test;

import de.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectValidInt {

    private static final String FOO = new String("BAR");

    private final int value;
    private transient boolean ignoreMe;

    public ObjectValidInt(int value){
        this.value = value;
        this.ignoreMe = true;
    }

    public int value(){
        return value;
    }

    public boolean ignoreMe(){
        return ignoreMe;
    }

    public static String getFoo(){
        return FOO;
    }
}