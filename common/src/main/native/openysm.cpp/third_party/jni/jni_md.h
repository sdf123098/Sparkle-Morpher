/*
 * Portable jni_md.h — combines Windows / Unix / Android conventions so the
 * same JNI source can be cross-compiled by Zig for all targets in this repo.
 * Derived from OpenJDK / Zulu jni_md.h (GPLv2 + Classpath exception).
 */

#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#if defined(_WIN32) || defined(_WIN64)

  #ifndef JNIEXPORT
    #define JNIEXPORT __declspec(dllexport)
  #endif
  #define JNIIMPORT __declspec(dllimport)
  #define JNICALL __stdcall

  typedef int jint;
  typedef long long jlong;
  typedef signed char jbyte;

#else /* POSIX: Linux, macOS, Android */

  #if (defined(__GNUC__) && ((__GNUC__ > 4) || (__GNUC__ == 4 && __GNUC_MINOR__ > 2))) || __has_attribute(visibility)
    #define JNIEXPORT __attribute__((visibility("default")))
    #define JNIIMPORT __attribute__((visibility("default")))
  #else
    #define JNIEXPORT
    #define JNIIMPORT
  #endif

  #define JNICALL

  typedef int jint;
  #ifdef _LP64
    typedef long jlong;
  #else
    typedef long long jlong;
  #endif
  typedef signed char jbyte;

#endif

#endif /* !_JAVASOFT_JNI_MD_H_ */
