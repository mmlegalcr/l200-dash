package com.serenegiant.usb;

/** Firma exigida por libuvccamera-lib.so: el código nativo llama onFrame([B)V. */
public interface IFrameCallback {
    void onFrame(byte[] frame);
}
