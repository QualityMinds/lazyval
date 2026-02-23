package scanarios.java;

import com.qualityminds.lazyval.LazyValue;
import util.IdGenerator;

import java.util.UUID;

@LazyValue
public record ProductId(String value) {

    public static ProductId of(String value){
        if(value == null){
            return null;
        }
        return new ProductId(value);
    }

    public static ProductId createNew(IdGenerator generator){
        return new ProductId(generator.generateId());
    }

    public static ProductId createNew(){
        return new ProductId(UUID.randomUUID().toString());
    }
}