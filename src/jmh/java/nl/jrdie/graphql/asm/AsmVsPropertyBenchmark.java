package nl.jrdie.graphql.asm;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class AsmVsPropertyBenchmark {

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputInDirectClassHierarchy(Blackhole blackhole) {
    executeTest(blackhole, dfeFoo);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputDirectClassHierarchy(Blackhole blackhole) {
    executeTest(blackhole, dfeBar);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputInDirectClassHierarchyMethodRef(Blackhole blackhole) {
    executeTestMethodReference(blackhole, dfeFoo);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputDirectClassHierarchyMethodRef(Blackhole blackhole) {
    executeTestMethodReference(blackhole, dfeBar);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputInDirectClassHierarchyAsm(Blackhole blackhole) {
    executeTestAsm(blackhole, dfeFoo);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputDirectClassHierarchyAsm(Blackhole blackhole) {
    executeTestAsm(blackhole, dfeBar);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputDirectMethod(Blackhole blackhole) throws Exception {
    executeTestDirectMethod(blackhole, dfeBar);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchMarkThroughputDirectField(Blackhole blackhole) throws Exception {
    executeTestDirectField(blackhole, dfeBar);
  }

  static PropertyDataFetcher<Object> nameFetcher = PropertyDataFetcher.fetching("name");
  static PropertyDataFetcher<Object> nameFetcherMethodReference =
      PropertyDataFetcher.fetching(Bar::getName);
  static AsmDataFetcher<Object> nameAsmFetcher = AsmDataFetcher.fetching("name");
  static DataFetcher<Object> directBarFetcherMethod =
      a -> {
        if (a == null) {
          return null;
        }
        return ((Bar) a.getSource()).getName();
      };
  static DataFetcher<Object> directBarFetcherField =
      a -> {
        if (a == null) {
          return null;
        }
        return ((Bar) a.getSource()).name;
      };

  static DataFetchingEnvironment dfeFoo =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Foo("brad")).build();
  static DataFetchingEnvironment dfeBar =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Bar("brad")).build();

  public static void executeTest(Blackhole blackhole, DataFetchingEnvironment dfe) {
    blackhole.consume(nameFetcher.get(dfe));
  }

  public static void executeTestMethodReference(Blackhole blackhole, DataFetchingEnvironment dfe) {
    blackhole.consume(nameFetcherMethodReference.get(dfe));
  }

  public static void executeTestAsm(Blackhole blackhole, DataFetchingEnvironment dfe) {
    blackhole.consume(nameAsmFetcher.get(dfe));
  }

  public static void executeTestDirectMethod(Blackhole blackhole, DataFetchingEnvironment dfe)
      throws Exception {
    blackhole.consume(directBarFetcherMethod.get(dfe));
  }

  public static void executeTestDirectField(Blackhole blackhole, DataFetchingEnvironment dfe)
      throws Exception {
    blackhole.consume(directBarFetcherField.get(dfe));
  }

  public static class Foo extends Bar {
    Foo(String name) {
      super(name);
    }
  }

  public static class Bar {
    private final String name;

    Bar(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
