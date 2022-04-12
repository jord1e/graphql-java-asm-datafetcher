package nl.jrdie.graphql.asm.internal;

import graphql.Scalars;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsmDataFetchingImpl {

  private static final Logger log = LoggerFactory.getLogger(AsmDataFetchingImpl.class);

  private final AtomicBoolean USE_NEGATIVE_CACHE = new AtomicBoolean(true);
  private final ConcurrentMap<CacheKey, CacheKey> NEGATIVE_CACHE = new ConcurrentHashMap<>();
  private final AtomicReference<AsmDataFetcherClassLoader> asmClassLoader = new AtomicReference<>();
  private final Class<?> singleArgumentType;
  private final String classNamePattern = "graphql.asm.generated.{id}";
  private final Random identifierRandom = new SecureRandom();

  // Stores the compound mapping of (source class + property) -> generated data fetcher class
  private final ConcurrentMap<CacheKey, Class<?>> DATA_FETCHER_TO_ASM_CLASS =
      new ConcurrentHashMap<>();

  public AsmDataFetchingImpl(Class<?> singleArgumentType) {
    this.singleArgumentType = singleArgumentType;
    resetClassLoader();
  }

  private String randomName() {
    return "AsmGeneratedDf" + Long.toString(identifierRandom.nextLong(), 16).replaceAll("^-", "");
  }

  private String getClassName(String id) {
    return classNamePattern.replace("{id}", id);
  }

  private String getClassDescriptorName(String id) {
    return classNamePattern.replace('.', '/').replace("{id}", id);
  }

  public Class<?> getOrCreateDelegateClass(
      String propertyName, Object source, GraphQLType graphQLType) {
    long buildTimeStartNanos = System.nanoTime();
    Class<?> sourceType = source.getClass();

    CacheKey cacheKey = mkCacheKey(source, propertyName);
    Class<?> cachedDataFetcherClass = DATA_FETCHER_TO_ASM_CLASS.get(cacheKey);
    if (cachedDataFetcherClass != null) {
      return cachedDataFetcherClass;
    }

    Method aMethod = getPropertyValue(propertyName, source, graphQLType, singleArgumentType);
    if (aMethod != null) {
      String randomClassNameIdentifier = randomName();
      String classDescriptor = getClassDescriptorName(randomClassNameIdentifier);
      byte[] classBytes =
          createPublicMethodDataFetcherClassBytes(classDescriptor, sourceType, aMethod);
      String className = getClassName(randomClassNameIdentifier);
      Class<?> clazz = asmClassLoader.get().defineClass(className, classBytes);
      DATA_FETCHER_TO_ASM_CLASS.put(cacheKey, clazz);
      long finalBuildTimeNanos = System.nanoTime() - buildTimeStartNanos;
      log.warn(
          "Building of initial ASM data fetcher class `"
              + clazz.getName()
              + "` targeting "
              + cacheKey
              + " took "
              + finalBuildTimeNanos
              + " nanoseconds (ns), this is approximately "
              + TimeUnit.NANOSECONDS.toMillis(finalBuildTimeNanos)
              + " milliseconds (ms)");
      return clazz;
    }
    return null;
  }

  public Method getPropertyValue(
      String propertyName, Object object, GraphQLType graphQLType, Object singleArgumentValue) {
    CacheKey cacheKey = mkCacheKey(object, propertyName);

    if (isNegativelyCached(cacheKey)) {
      return null;
    }

    boolean dfeInUse = singleArgumentValue != null;
    String[] lookupMethods =
        getMethodNamesForClassAndProperty(propertyName, isBooleanProperty(graphQLType));
    for (String lookupMethod : lookupMethods) {
      Optional<Method> m = findPubliclyAccessibleMethod(object.getClass(), lookupMethod, dfeInUse);
      if (m.isPresent()) {
        return m.get();
      }
    }

    log.warn(
        "Couldn't find publicly accessible method in inheritance tree of `"
            + object.getClass().getName()
            + "` that resolves the `"
            + propertyName
            + "` property. We searched for: "
            + Arrays.toString(lookupMethods));
    putInNegativeCache(cacheKey);
    return null;
  }

  private boolean isNegativelyCached(CacheKey key) {
    if (USE_NEGATIVE_CACHE.get()) {
      return NEGATIVE_CACHE.containsKey(key);
    }
    return false;
  }

  private void putInNegativeCache(CacheKey key) {
    if (USE_NEGATIVE_CACHE.get()) {
      NEGATIVE_CACHE.put(key, key);
    }
  }

  /**
   * Invoking public methods on package-protected classes via reflection causes exceptions. This
   * method searches a class's hierarchy for public visibility parent classes with the desired
   * getter. This particular case is required to support AutoValue style data classes, which have
   * abstract public interfaces implemented by package-protected (generated) subclasses.
   */
  private Optional<Method> findPubliclyAccessibleMethod(
      Class<?> rootClass, String methodName, boolean dfeInUse) {
    Class<?> currentClass = rootClass;
    boolean oneIsNotPublic = false;
    Method m = null;
    while (currentClass != null) {
      if (Modifier.isPublic(currentClass.getModifiers())) {
        if (dfeInUse) {
          // Try a method that takes singleArgumentType first (if we have one)
          try {
            Method method = currentClass.getMethod(methodName, singleArgumentType);
            if (Modifier.isPublic(method.getModifiers())) {
              m = method;
              break;
            }
          } catch (NoSuchMethodException ignored) {
          }
        }
        // Try a method without parameters
        try {
          Method method = currentClass.getMethod(methodName);
          if (Modifier.isPublic(method.getModifiers())) {
            m = method;
            break;
          }
        } catch (NoSuchMethodException e) {
          break;
        }
      } else {
        oneIsNotPublic = true;
      }
      currentClass = currentClass.getSuperclass();
    }

    if (m != null) {
      return Optional.of(m);
    }

    if (oneIsNotPublic) {
      log.warn(
          "No publicly accessible classes in the inheritance tree of `"
              + rootClass.getName()
              + "` (lookup for `"
              + methodName
              + "` method)");
      return Optional.empty();
    }

    assert rootClass != null;
    try {
      return Optional.of(rootClass.getMethod(methodName));
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
  }

  private boolean isBooleanProperty(GraphQLType graphQLType) {
    if (graphQLType == Scalars.GraphQLBoolean) {
      return true;
    }
    if (GraphQLTypeUtil.isNonNull(graphQLType)) {
      return GraphQLTypeUtil.unwrapOne(graphQLType) == Scalars.GraphQLBoolean;
    }
    return false;
  }

  private String[] getMethodNamesForClassAndProperty(String propertyName, boolean isBooleanType) {
    if (isBooleanType) {
      return new String[] {
        capitalizeWithPrefix(propertyName, "is"), capitalizeWithPrefix(propertyName, "get")
      };
    }
    return new String[] {
      capitalizeWithPrefix(propertyName, "get"), capitalizeWithPrefix(propertyName, "test")
    };
  }

  private String capitalizeWithPrefix(String toBePrefixed, String prefix) {
    return prefix + toBePrefixed.substring(0, 1).toUpperCase() + toBePrefixed.substring(1);
  }

  private void resetClassLoader() {
    asmClassLoader.set(new AsmDataFetcherClassLoader());
  }

  private void clearReflectionCache() {
    NEGATIVE_CACHE.clear();
    DATA_FETCHER_TO_ASM_CLASS.clear();
    resetClassLoader();
  }

  private boolean setUseNegativeCache(boolean flag) {
    return USE_NEGATIVE_CACHE.getAndSet(flag);
  }

  private CacheKey mkCacheKey(Object object, String propertyName) {
    Class<?> clazz = object.getClass();
    ClassLoader classLoader = clazz.getClassLoader();
    return new CacheKey(classLoader, clazz.getName(), propertyName);
  }

  private byte[] createPublicMethodDataFetcherClassBytes(
      String classDescriptor, Class<?> sourceClass, Method targetMethod) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        classDescriptor,
        null,
        "java/lang/Object",
        new String[] {"graphql/schema/DataFetcher"});

    MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);

    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1); // `this`

    MethodVisitor log =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "get",
            "(Lgraphql/schema/DataFetchingEnvironment;)Ljava/lang/Object;",
            null,
            null);

    log.visitCode();
    log.visitVarInsn(Opcodes.ALOAD, 1); // Load environment
    log.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "graphql/schema/DataFetchingEnvironment",
        "getSource",
        "()Ljava/lang/Object;",
        true);
    log.visitVarInsn(Opcodes.ASTORE, 2); // Store source onto stack
    Label sourceNullLabel = new Label();
    log.visitVarInsn(Opcodes.ALOAD, 2); // Load source onto stack
    // Check if source is null
    log.visitJumpInsn(Opcodes.IFNONNULL, sourceNullLabel);
    log.visitInsn(Opcodes.ACONST_NULL);
    log.visitInsn(Opcodes.ARETURN);
    // Was not null \/
    log.visitLabel(sourceNullLabel);
    log.visitVarInsn(Opcodes.ALOAD, 2); // Load source onto stack
    final String internalName = Type.getInternalName(targetMethod.getDeclaringClass());
    log.visitTypeInsn(Opcodes.CHECKCAST, internalName); // Cast source to T
    log.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        internalName,
        targetMethod.getName(),
        Type.getMethodDescriptor(targetMethod),
        false);
    new GeneratorAdapter(
            log,
            Opcodes.ACC_PUBLIC,
            "get",
            "(Lgraphql/schema/DataFetchingEnvironment;)Ljava/lang/Object;")
        .valueOf(Type.getType(targetMethod.getReturnType()));
    log.visitInsn(Opcodes.ARETURN);
    log.visitMaxs(1, 3); // `this`, `environment`, `source`

    cw.visitEnd();

    return cw.toByteArray();
  }

  private static final class CacheKey {
    private final ClassLoader classLoader;
    private final String className;
    private final String propertyName;

    private CacheKey(ClassLoader classLoader, String className, String propertyName) {
      this.classLoader = classLoader;
      this.className = className;
      this.propertyName = propertyName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CacheKey)) return false;
      CacheKey cacheKey = (CacheKey) o;
      return Objects.equals(classLoader, cacheKey.classLoader)
          && Objects.equals(className, cacheKey.className)
          && Objects.equals(propertyName, cacheKey.propertyName);
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(classLoader);
      result = 31 * result + Objects.hashCode(className);
      result = 31 * result + Objects.hashCode(propertyName);
      return result;
    }

    @Override
    public String toString() {
      return "CacheKey{"
          + "classLoader="
          + classLoader
          + ", className='"
          + className
          + '\''
          + ", propertyName='"
          + propertyName
          + '\''
          + '}';
    }
  }

  private static final class AsmDataFetcherClassLoader extends ClassLoader {
    Class<?> defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }
}
