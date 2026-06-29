package test.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

import javax.annotation.processing.Generated;
import java.time.LocalDate;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.cassandra.CassandraCodecGenerator")
public final class LazyvalCassandraCodecs {
  private LazyvalCassandraCodecs() {
  }

  /**
   * Returns an array of all generated {@link TypeCodec}s for lazyval wrapper types.
   * <p>
   * Use this method to register all codecs at once, e.g.:
   * <pre>{@code
   * CqlSession session = CqlSession.builder()
   *     .addTypeCodecs(LazyvalCassandraCodecs.all())
   *     .build();
   * }</pre>
   * <p>
   * User-supplied codecs configured via {@code lazyval.cassandra.codecs} are
   * appended to the array so they take precedence in DataStax's last-registered-wins
   * resolution.
   *
   * @return an array containing one codec instance per generated wrapper type,
   * followed by one instance of each user-supplied codec
   */
  public static TypeCodec<?>[] all() {
    return new TypeCodec[] {
            new IdsProductIdCodec(),
            new IsbnCodec(),
            new OrderDateCodec(),
            new QuantityCodec()
        };
  }

  static class IdsProductIdCodec extends MappingCodec<String, Ids.ProductId> {
    public IdsProductIdCodec() {
      super(TypeCodecs.TEXT, GenericType.of(Ids.ProductId.class));
    }

    @Override
    protected Ids.ProductId innerToOuter(String value) {
      if (value == null) {
        return null;
      }
      return Ids.ProductId.of(value);
    }

    @Override
    protected String outerToInner(Ids.ProductId value) {
      if (value == null) {
        return null;
      }
      return value.value();
    }
  }

  static class IsbnCodec extends MappingCodec<String, Isbn> {
    public IsbnCodec() {
      super(TypeCodecs.TEXT, GenericType.of(Isbn.class));
    }

    @Override
    protected Isbn innerToOuter(String value) {
      if (value == null) {
        return null;
      }
      return Isbn.parse(value);
    }

    @Override
    protected String outerToInner(Isbn value) {
      if (value == null) {
        return null;
      }
      return value.getValue();
    }
  }

  static class OrderDateCodec extends MappingCodec<LocalDate, OrderDate> {
    public OrderDateCodec() {
      super(TypeCodecs.DATE, GenericType.of(OrderDate.class));
    }

    @Override
    protected OrderDate innerToOuter(LocalDate value) {
      if (value == null) {
        return null;
      }
      return new OrderDate(value);
    }

    @Override
    protected LocalDate outerToInner(OrderDate value) {
      if (value == null) {
        return null;
      }
      return value.value();
    }
  }

  static class QuantityCodec extends MappingCodec<Integer, Quantity> {
    public QuantityCodec() {
      super(TypeCodecs.INT, GenericType.of(Quantity.class));
    }

    @Override
    protected Quantity innerToOuter(Integer value) {
      if (value == null) {
        return null;
      }
      return new Quantity(value);
    }

    @Override
    protected Integer outerToInner(Quantity value) {
      if (value == null) {
        return null;
      }
      return value.value();
    }
  }
}
