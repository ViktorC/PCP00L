/*
 * JNITest.cpp
 *
 *  Created on: 12.09.2017
 *      Author: Viktor Csomor
 */

#include "jni.h"
#include "net_viktorc_pp4j_impl_WrapperJNI.h"
#include "test.h"

JNIEXPORT void JNICALL Java_net_viktorc_pp4j_impl_WrapperJNI_doStuff(JNIEnv * env,
		jclass clazz, jint seconds) {
	doStuff(seconds);
	return;
}
