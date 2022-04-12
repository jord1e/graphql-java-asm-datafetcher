package nl.jrdie.graphql.asm;

import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

public class TestMain {
  static DataFetchingEnvironment dfeBar =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Bar("brad")).build();
  static DataFetchingEnvironment dfeFoo =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Foo("A")).build();
  static DataFetchingEnvironment dfeI =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Baz(1)).build();
  static AsmDataFetcher<Object> nameAsmFetcher =
      AsmDataFetcher.fetching("name", new PropertyDataFetcher<>("name"));
  static AsmDataFetcher<Object> b = AsmDataFetcher.fetching("b", new PropertyDataFetcher<>("b"));

  public static void main(String[] args) {
    System.out.println(nameAsmFetcher.get(dfeFoo));
    b.get(dfeFoo);
    TypeDefinitionRegistry tdr =
        new SchemaParser()
            .parse(
                "type Query { testClass(a: String!): TestClass! testClass1(a: Int!): TestClass1! } type TestClass { a: String! } type TestClass1 { a: Int! }");

    RuntimeWiring rtw =
        RuntimeWiring.newRuntimeWiring()
            .codeRegistry(
                GraphQLCodeRegistry.newCodeRegistry()
                    .defaultDataFetcher(
                        AsmDataFetcherFactory.createFactory(
                            factoryEnvironment ->
                                PropertyDataFetcher.fetching(
                                    factoryEnvironment.getFieldDefinition().getName())))
                    .build())
            .type(
                "Query",
                env ->
                    env.dataFetcher(
                            "testClass",
                            testClassEnv -> new TestClass(testClassEnv.getArgument("a")))
                        .dataFetcher(
                            "testClass1",
                            testClassEnv -> new TestClassInt(testClassEnv.getArgument("a"))))
            //            .type("TestClass", env ->
            // env.defaultDataFetcher(AsmDataFetcher.fetching("a")))
            .build();

    GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(tdr, rtw);

    GraphQL graphQL = GraphQL.newGraphQL(schema).build();

    System.out.println(
        graphQL.execute("{ testClass(a: \"a\") { a } testClass1(a: 1) { a } }").toSpecification());
  }

  public static class Foo extends Bar {
    Foo(String name) {
      super(name);
    }
  }

  public static class Baz {
    private final int i;

    Baz(int i) {
      this.i = i;
    }

    public int getName() {
      return i;
    }
  }

  static class Bar {
    private final String name;

    Bar(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public String getB() {
      return name;
    }
  }
}
