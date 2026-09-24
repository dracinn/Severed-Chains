#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

typedef jint (*jni_create_java_vm_fn)(JavaVM **vm, void **environment, void *arguments);

JNIEXPORT jstring JNICALL
Java_org_legendofdragoon_severedchains_android_runtime_NativeRuntimeBridge_inspectJvm(
    JNIEnv *env,
    jclass clazz,
    jstring absolute_jvm_path) {
  (void) clazz;
  const char *path = (*env)->GetStringUTFChars(env, absolute_jvm_path, NULL);
  if (path == NULL) {
    return (*env)->NewStringUTF(env, "could not read JVM path");
  }

  void *library = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
  if (library == NULL) {
    const char *error = dlerror();
    char message[512];
    snprintf(message, sizeof(message), "libjvm.so load failed: %s", error == NULL ? "unknown error" : error);
    (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, path);
    return (*env)->NewStringUTF(env, message);
  }

  jni_create_java_vm_fn create_vm = (jni_create_java_vm_fn) dlsym(library, "JNI_CreateJavaVM");
  const char *message = create_vm == NULL
      ? "libjvm.so loaded, but JNI_CreateJavaVM is missing"
      : "libjvm.so and JNI_CreateJavaVM are ready";
  dlclose(library);
  (*env)->ReleaseStringUTFChars(env, absolute_jvm_path, path);
  return (*env)->NewStringUTF(env, message);
}
