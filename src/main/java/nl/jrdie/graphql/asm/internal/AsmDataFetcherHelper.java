package nl.jrdie.graphql.asm.internal;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLType;
import nl.jrdie.graphql.asm.AsmDataFetcherException;

public class AsmDataFetcherHelper {

  private static final AsmDataFetchingImpl impl =
      new AsmDataFetchingImpl(DataFetchingEnvironment.class);

  @SuppressWarnings("unchecked")
  public static <T> DataFetcher<T> createDelegate(
      String propertyName, Object source, GraphQLType graphQLType) {
    Class<?> dataFetcherClass = impl.getOrCreateDelegateClass(propertyName, source, graphQLType);
    if (dataFetcherClass == null) {
      return null;
    }

    try {
      return (DataFetcher<T>) dataFetcherClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new AsmDataFetcherException(e);
    }
  }
}
