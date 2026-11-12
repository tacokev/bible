package com.example.bible.data;

import kotlin.jvm.functions.Function1;

/**
 * Audio callback for sherpa-onnx's {@code generateWithCallback}. Its JNI code looks the callback
 * method up by the exact signature {@code Integer invoke(float[])}; Kotlin 2 lambdas only have the
 * erased {@code Object invoke(Object)}, which makes the native side abort. Written in Java so
 * that signature exists. Return 1 to keep generating, 0 to stop.
 */
final class SherpaCallback implements Function1<float[], Integer> {
    interface Sink {
        boolean accept(float[] samples);
    }

    private final Sink sink;

    SherpaCallback(Sink sink) {
        this.sink = sink;
    }

    @Override
    public Integer invoke(float[] samples) {
        return sink.accept(samples) ? 1 : 0;
    }
}
