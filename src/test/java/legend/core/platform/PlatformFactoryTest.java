package legend.core.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlatformFactoryTest {
  @Test
  void loadsTheConfiguredPlatformManager() {
    final String previous = System.getProperty(PlatformFactory.PLATFORM_CLASS_PROPERTY);
    System.setProperty(PlatformFactory.PLATFORM_CLASS_PROPERTY, NoopPlatformManager.class.getName());

    try {
      assertInstanceOf(NoopPlatformManager.class, PlatformFactory.create());
    } finally {
      if(previous == null) {
        System.clearProperty(PlatformFactory.PLATFORM_CLASS_PROPERTY);
      } else {
        System.setProperty(PlatformFactory.PLATFORM_CLASS_PROPERTY, previous);
      }
    }
  }
}
