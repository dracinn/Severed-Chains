package legend.core.platform;

/**
 * Selects the host platform implementation before the engine begins loading graphics and input.
 * Desktop builds retain SDL as the default; alternate hosts provide a {@link PlatformManager}
 * implementation in the game JVM class path and select it with {@value #PLATFORM_CLASS_PROPERTY}.
 */
public final class PlatformFactory {
  public static final String PLATFORM_CLASS_PROPERTY = "legend.platform.class";
  private static final String DEFAULT_PLATFORM_CLASS = "legend.core.platform.SdlPlatformManager";

  private PlatformFactory() { }

  public static PlatformManager create() {
    final String className = System.getProperty(PLATFORM_CLASS_PROPERTY, DEFAULT_PLATFORM_CLASS);

    try {
      final Class<?> candidate = Class.forName(className);
      if(!PlatformManager.class.isAssignableFrom(candidate)) {
        throw new IllegalStateException(className + " does not extend PlatformManager");
      }

      return candidate.asSubclass(PlatformManager.class).getDeclaredConstructor().newInstance();
    } catch(final ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to create platform manager " + className, e);
    }
  }
}
