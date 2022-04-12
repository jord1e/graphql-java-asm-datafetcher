package nl.jrdie.graphql.asm;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetcherFactoryEnvironment;

public final class AsmDataFetcherFactory<T> implements DataFetcherFactory<T> {

  private final DataFetcherFactory<T> fallbackFactory;

  private AsmDataFetcherFactory(DataFetcherFactory<T> fallbackFactory) {
    this.fallbackFactory = fallbackFactory;
  }

  public static <T> AsmDataFetcherFactory<T> createFactory() {
    return new AsmDataFetcherFactory<>(null);
  }

  public static <T> AsmDataFetcherFactory<T> createFactory(DataFetcherFactory<T> fallbackFactory) {
    return new AsmDataFetcherFactory<>(fallbackFactory);
  }

  @Override
  public DataFetcher<T> get(DataFetcherFactoryEnvironment factoryEnvironment) {
    final String fieldName = factoryEnvironment.getFieldDefinition().getName();
    if (this.fallbackFactory == null) {
      return AsmDataFetcher.fetching(fieldName);
    } else {
      return AsmDataFetcher.fetching(fieldName, this.fallbackFactory.get(factoryEnvironment));
    }
  }
}
