/**
 * JNI wrapper for the Fortran raytracer/linear algebra module.
 * Bridges Java's nativeCompute() to Fortran's fortran_compute().
 *
 * The fact that this C file exists solely to let Java call Fortran
 * via JNI to raytrace a sphere for a calculator app is noted.
 */
#include <jni.h>
#include <stdint.h>

/* Fortran bind(C) functions — pass everything by pointer */
extern void fortran_compute(int32_t *result, double *eigenvalues,
                            double *singular_values, int32_t *pixel_checksum);
extern void fortran_get_pixels(int8_t *rgba_out);

/* 64x64 RGBA */
#define PIXEL_BYTES (64 * 64 * 4)

JNIEXPORT void JNICALL
Java_edu_singaporetech_inf2007quiz01_FortranBridge_nativeCompute(
    JNIEnv *env, jobject thiz,
    jint result,
    jdoubleArray eigenvalues,
    jdoubleArray singularValues,
    jintArray pixelChecksum)
{
    (void)thiz;

    int32_t res = (int32_t)result;
    double eigen[4];
    double svd[4];
    int32_t checksum;

    fortran_compute(&res, eigen, svd, &checksum);

    (*env)->SetDoubleArrayRegion(env, eigenvalues, 0, 4, eigen);
    (*env)->SetDoubleArrayRegion(env, singularValues, 0, 4, svd);
    (*env)->SetIntArrayRegion(env, pixelChecksum, 0, 1, &checksum);
}

/* Export the 64x64 RGBA framebuffer for OpenGL texture upload */
JNIEXPORT jbyteArray JNICALL
Java_edu_singaporetech_inf2007quiz01_FortranBridge_nativeGetPixels(
    JNIEnv *env, jobject thiz)
{
    (void)thiz;
    int8_t pixels[PIXEL_BYTES];
    fortran_get_pixels(pixels);

    jbyteArray arr = (*env)->NewByteArray(env, PIXEL_BYTES);
    if (arr) {
        (*env)->SetByteArrayRegion(env, arr, 0, PIXEL_BYTES, pixels);
    }
    return arr;
}
