package com.serenegiant.usb;

/** Firma exigida por libuvccamera-lib.so: el código nativo llama onEvent(I)V. */
public interface IEventCallback {
    void onEvent(int event);
}
