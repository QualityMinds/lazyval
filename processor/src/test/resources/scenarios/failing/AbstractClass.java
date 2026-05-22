package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public abstract class AbstractClass {

    private final String value;

    public AbstractClass(String value){
        this.value = value;
    }

    public String value(){
        return value;
    }
}