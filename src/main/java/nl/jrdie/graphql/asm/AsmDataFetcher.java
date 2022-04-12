package nl.jrdie.graphql.asm;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;
import nl.jrdie.graphql.asm.internal.AsmDataFetcherHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsmDataFetcher<T> implements DataFetcher<T> {

  private static final Logger log = LoggerFactory.getLogger(AsmDataFetcher.class);

  private final String propertyName;
  private final DataFetcher<T> fallback;
  private boolean hasBeenResolved;
  private DataFetcher<T> delegate;

  private AsmDataFetcher(String propertyName, DataFetcher<T> fallback) {
    this.propertyName = Objects.requireNonNull(propertyName, "propertyName");
    this.fallback = fallback;
  }

  public static <T> AsmDataFetcher<T> fetching(String propertyName) {
    return new AsmDataFetcher<>(propertyName, null);
  }

  public static <T> AsmDataFetcher<T> fetching(String propertyName, DataFetcher<T> fallback) {
    return new AsmDataFetcher<>(propertyName, fallback);
  }

  @Override
  public T get(DataFetchingEnvironment environment) {
    //        if (!Objects.equals(environment.getField().getName(), this.propertyName)) {
    //            throw new IllegalArgumentException();
    //        }
    if (!hasBeenResolved && this.delegate == null) {
      this.delegate =
          AsmDataFetcherHelper.createDelegate(
              propertyName, environment.getSource(), environment.getFieldType());
      if (this.delegate == null) {
        if (this.fallback == null) {
          log.warn(
              "No ASM data fetcher could be generated for property `"
                  + this.propertyName
                  + "`, and no fallback data fetcher was provided");
        } else {
          log.warn(
              "No ASM data fetcher could be generated for property `"
                  + this.propertyName
                  + "`, using fallback "
                  + this.fallback
                  + " data fetcher");
          this.delegate = this.fallback;
        }
      } else {
        log.warn(
            "A valid ASM data fetcher has been lazily assigned to a data fetcher targeting the `"
                + this.propertyName
                + "` property");
      }
      hasBeenResolved = true;
    }
    try {
      if (this.delegate != null) {
        return this.delegate.get(environment);
      }
    } catch (Exception e) {
      // Instead of throwing RuntimeExceptions, DataFetchers throw Exceptions :,(
      throw new AsmDataFetcherPermeatingException(e);
    }
    throw new AsmDataFetcherException(
        "No ASM data fetcher could be generated for field targeting property `"
            + propertyName
            + "`, and no fallback data fetcher was specified. The DFE was: ["
            + environment
            + "]");
  }
}
