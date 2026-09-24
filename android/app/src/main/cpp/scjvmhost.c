#include <jni.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <stdio.h>
#include <string.h>

typedef jint (*jni_create_java_vm_fn)(JavaVM **vm, void **environment, void *arguments);

static EGLDisplay egl_display = EGL_NO_DISPLAY;
static EGLSurface egl_surface = EGL_NO_SURFACE;
static EGLContext egl_context = EGL_NO_CONTEXT;
static ANativeWindow *egl_window = NULL;

static void stop_egl_surface(void) {
  if (egl_display != EGL_NO_DISPLAY) {
    eglMakeCurrent(egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (egl_context != EGL_NO_CONTEXT) eglDestroyContext(egl_display, egl_context);
    if (egl_surface != EGL_NO_SURFACE) eglDestroySurface(egl_display, egl_surface);
    eglTerminate(egl_display);
  }
  if (egl_window != NULL) ANativeWindow_release(egl_window);
  egl_display = EGL_NO_DISPLAY;
  egl_surface = EGL_NO_SURFACE;
  egl_context = EGL_NO_CONTEXT;
  egl_window = NULL;
}

static jstring make_string(JNIEnv *env, const char *message) {
  return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jstring JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeRuntimeBridge_inspectJvm(
    JNIEnv *env,
    jclass clazz,
    jstring absolute_jvm_path) {
  (void) clazz;
  const char *path = (*env)->GetStringUTFChars(env, absolute_jvm_path, NULL);
  if (path == NULL) {
    return make_string(env, "could not read JVM path");
  }

  void *library = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
  if (library == NULL) {
    const char *error = dlerror();
    char message[512];
    snprintf(message, sizeof(message), "libjvm.so load failed: %s", error == NULL ? "unknown error" : error);
    (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, path);
    return make_string(env, message);
  }

  jni_create_java_vm_fn create_vm = (jni_create_java_vm_fn) dlsym(library, "JNI_CreateJavaVM");
  const char *message = create_vm == NULL
      ? "libjvm.so loaded, but JNI_CreateJavaVM is missing"
      : "libjvm.so and JNI_CreateJavaVM are ready";
  dlclose(library);
  (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, path);
  return make_string(env, message);
}

JNIEXPORT jstring JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeRuntimeBridge_probeJvm(
    JNIEnv *env,
    jclass clazz,
    jstring absolute_jvm_path,
    jstring java_home) {
  (void) clazz;
  const char *jvm_path = (*env)->GetStringUTFChars(env, absolute_jvm_path, NULL);
  const char *home = (*env)->GetStringUTFChars(env, java_home, NULL);
  if (jvm_path == NULL || home == NULL) {
    if (jvm_path != NULL) (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, jvm_path);
    if (home != NULL) (*env)->ReleaseStringUTFChars(env, java_home, home);
    return make_string(env, "could not read JRE paths");
  }

  void *library = dlopen(jvm_path, RTLD_NOW | RTLD_GLOBAL);
  if (library == NULL) {
    const char *error = dlerror();
    char message[512];
    snprintf(message, sizeof(message), "libjvm.so load failed: %s", error == NULL ? "unknown error" : error);
    (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, jvm_path);
    (*env)->ReleaseStringUTFChars(env, java_home, home);
    return make_string(env, message);
  }

  jni_create_java_vm_fn create_vm = (jni_create_java_vm_fn) dlsym(library, "JNI_CreateJavaVM");
  if (create_vm == NULL) {
    dlclose(library);
    (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, jvm_path);
    (*env)->ReleaseStringUTFChars(env, java_home, home);
    return make_string(env, "JNI_CreateJavaVM is missing");
  }

  char home_option[1024];
  snprintf(home_option, sizeof(home_option), "-Djava.home=%s", home);
  JavaVMOption options[] = {
      {.optionString = home_option},
      {.optionString = "-Djava.class.path="},
      {.optionString = "-Duser.dir=/"},
  };
  JavaVMInitArgs arguments = {
      // JNI 1.6 is the newest ABI constant exposed by Android's NDK headers.
      // A JRE 25 JVM supports this stable JNI ABI.
      .version = JNI_VERSION_1_6,
      .nOptions = (jint) (sizeof(options) / sizeof(options[0])),
      .options = options,
      .ignoreUnrecognized = JNI_TRUE,
  };
  JavaVM *vm = NULL;
  JNIEnv *vm_env = NULL;
  const jint result = create_vm(&vm, (void **) &vm_env, &arguments);
  if (result != JNI_OK || vm_env == NULL) {
    char message[128];
    snprintf(message, sizeof(message), "JNI_CreateJavaVM failed: %d", result);
    dlclose(library);
    (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, jvm_path);
    (*env)->ReleaseStringUTFChars(env, java_home, home);
    return make_string(env, message);
  }

  jclass system_class = (*vm_env)->FindClass(vm_env, "java/lang/System");
  jmethodID get_property = system_class == NULL ? NULL
      : (*vm_env)->GetStaticMethodID(vm_env, system_class, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
  jstring property = (*vm_env)->NewStringUTF(vm_env, "java.version");
  jstring version = get_property == NULL ? NULL
      : (jstring) (*vm_env)->CallStaticObjectMethod(vm_env, system_class, get_property, property);
  const char *version_chars = version == NULL ? NULL : (*vm_env)->GetStringUTFChars(vm_env, version, NULL);
  char message[256];
  snprintf(message, sizeof(message), "JRE VM started%s%s", version_chars == NULL ? "" : ": Java ",
      version_chars == NULL ? "" : version_chars);
  if (version_chars != NULL) (*vm_env)->ReleaseStringUTFChars(vm_env, version, version_chars);
  (*vm)->DestroyJavaVM(vm);
  dlclose(library);
  (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, jvm_path);
  (*env)->ReleaseStringUTFChars(env, java_home, home);
  return make_string(env, message);
}

JNIEXPORT jstring JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeEglSurface_start(
    JNIEnv *env, jclass clazz, jobject surface) {
  (void) clazz;
  stop_egl_surface();
  egl_window = ANativeWindow_fromSurface(env, surface);
  if (egl_window == NULL) return make_string(env, "Android surface is unavailable");
  egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  if (egl_display == EGL_NO_DISPLAY || !eglInitialize(egl_display, NULL, NULL)) goto failure;
  const EGLint config_attributes[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE,
      EGL_WINDOW_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
  EGLConfig config;
  EGLint config_count;
  if (!eglChooseConfig(egl_display, config_attributes, &config, 1, &config_count) || config_count == 0) goto failure;
  const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
  egl_surface = eglCreateWindowSurface(egl_display, config, egl_window, NULL);
  egl_context = eglCreateContext(egl_display, config, EGL_NO_CONTEXT, context_attributes);
  if (egl_surface == EGL_NO_SURFACE || egl_context == EGL_NO_CONTEXT ||
      !eglMakeCurrent(egl_display, egl_surface, egl_surface, egl_context)) goto failure;
  glClearColor(0.04f, 0.08f, 0.16f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT);
  eglSwapBuffers(egl_display, egl_surface);
  return NULL;
failure:
  stop_egl_surface();
  return make_string(env, "could not create an OpenGL ES 3 window surface");
}

JNIEXPORT void JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeEglSurface_resize(
    JNIEnv *env, jclass clazz, jint width, jint height) {
  (void) env; (void) clazz;
  if (egl_display == EGL_NO_DISPLAY || egl_surface == EGL_NO_SURFACE) return;
  glViewport(0, 0, width, height);
  glClearColor(0.04f, 0.08f, 0.16f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT);
  eglSwapBuffers(egl_display, egl_surface);
}

JNIEXPORT void JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeEglSurface_stop(
    JNIEnv *env, jclass clazz) {
  (void) env; (void) clazz;
  stop_egl_surface();
}
