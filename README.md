# AsmDataFetcher for GraphQL Java

TODO: Add more text

## Usage

Without fallback to PropertyDataFetcher (does not work for fields, or -currently- records):

```java
RuntimeWiring rtw =
    RuntimeWiring.newRuntimeWiring()
        .codeRegistry(
            GraphQLCodeRegistry.newCodeRegistry()
                .defaultDataFetcher(
                    AsmDataFetcherFactory.createFactory())
                .build())
```

With fallback to PropertyDataFetcher:

```java
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
```

## Benchmarks

Preliminary benchmarks:

```
Benchmark                                                             Mode  Cnt         Score         Error  Units
AsmVsPropertyBenchmark.benchMarkThroughputDirectClassHierarchy       thrpt   15   6642515,159 ±  187660,195  ops/s
AsmVsPropertyBenchmark.benchMarkThroughputDirectClassHierarchyAsm    thrpt   15  77054169,782 ±  566113,042  ops/s
AsmVsPropertyBenchmark.benchMarkThroughputInDirectClassHierarchy     thrpt   15   6685492,720 ±   11685,971  ops/s
AsmVsPropertyBenchmark.benchMarkThroughputInDirectClassHierarchyAsm  thrpt   15  73568498,193 ± 5513132,975  ops/s
```

This is around a 11x performance speedup.
