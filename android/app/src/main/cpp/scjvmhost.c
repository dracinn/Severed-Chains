#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

typedef jint (*jni_create_java_vm_fn)(JavaVM **vm, void **environment, void *arguments);

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
