/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include "Algorithm.c"
/* Header for class io_agora_karaoke_view_v11_ai_AINative */

#ifndef _Included_io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
#define _Included_io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
 * Method:    nativePitchToTone
 * Signature: (D)D
 */
JNIEXPORT jdouble

JNICALL Java_io_agora_karaoke_1view_1ex_internal_ai_AIAlgorithmScoreNative_nativePitchToTone
        (JNIEnv *, jclass, jdouble j_pitch) {
    return pitchToToneC(j_pitch);
}

/*
 * Class:     io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
 * Method:    nativeCalculatedScore
 * Signature: (DDII)F
 */
JNIEXPORT jfloat

JNICALL Java_io_agora_karaoke_1view_1ex_internal_ai_AIAlgorithmScoreNative_nativeCalculatedScore
        (JNIEnv *, jclass, jdouble j_voicePitch, jdouble j_stdPitch, jint j_scoreLevel,
         jint j_scoreCompensationOffset) {
    return calculatedScoreC(j_voicePitch, j_stdPitch, j_scoreLevel, j_scoreCompensationOffset);
}

/*
 * Class:     io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
 * Method:    nativeHandlePitch
 * Signature: (DDD)D
 */
JNIEXPORT jdouble

JNICALL Java_io_agora_karaoke_1view_1ex_internal_ai_AIAlgorithmScoreNative_nativeHandlePitch
        (JNIEnv *, jclass, jdouble j_stdPitch, jdouble j_voicePitch, jdouble j_stdMaxPitch) {
    return handlePitchC(j_stdPitch, j_voicePitch, j_stdMaxPitch);
}

/*
 * Class:     io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative
 * Method:    nativeReset
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_io_agora_karaoke_1view_1ex_internal_ai_AIAlgorithmScoreNative_nativeReset
        (JNIEnv
         *, jclass) {
    resetC();
}

#ifdef __cplusplus
}
#endif
#endif
