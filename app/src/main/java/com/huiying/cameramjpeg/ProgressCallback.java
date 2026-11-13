package com.huiying.cameramjpeg;

/** Firma exigida por libuvccamera-lib.so para descargas: onProgress(I)V. */
public interface ProgressCallback {
    void onProgress(int percent);
}
