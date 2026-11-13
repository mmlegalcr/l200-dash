package com.huiying.cameramjpeg;

import android.view.Surface;

import com.serenegiant.usb.IEventCallback;
import com.serenegiant.usb.IFrameCallback;

/**
 * Puente JNI hacia libuvccamera-lib.so (la librería que trae la dashcam "USB CAR DVR").
 * El nombre del paquete, de la clase y las firmas de los métodos nativos DEBEN ser
 * exactamente estos: la librería exporta funciones Java_com_huiying_cameramjpeg_UvcCamera_*.
 * Este archivo solo declara la interfaz; la lógica de la app está en Kotlin.
 */
public class UvcCamera {

    static {
        System.loadLibrary("uvccamera-lib");
    }

    /** La librería llama a este método estático con los cuadros de audio/video de reproducción. */
    private static IFrameCallback sFrameCallback;

    public static void setStaticFrameCallback(IFrameCallback cb) {
        sFrameCallback = cb;
    }

    public static void onFrameData(byte[] data) {
        IFrameCallback cb = sFrameCallback;
        if (data != null && cb != null) cb.onFrame(data);
    }

    // ---- Inicialización (protocolo 100: archivos especiales en el disco USB de la cámara)
    /** Abre el archivo de video (…/VSFILE). Devuelve "fd_mensaje"; fd &gt; 0 = éxito. */
    public native String initUvcCamera(String devPath);
    /** Abre el archivo de comandos (…/Android/data/com.fvision.camera/HWC). Devuelve "fd_mensaje". */
    public native String initUvcCmd(String cmdPath);
    public native int initUvcFile(String path);
    public native int releaseUvcCamera(int fd);
    public native int setPx3(boolean px3);

    // ---- Vista previa
    public native int startPreview(int fd);
    public native int stopPreview(int fd);
    public native int GetStatus();
    public native void nativeSetMainRuning(boolean running);
    public native boolean GetLibDispStatus();
    public native void SetLibDispStatus(boolean show);

    // ---- Dibujo (la librería decodifica y dibuja con OpenGL ES 2 en el GLSurfaceView actual)
    public native void initGles(int width, int height);
    public native void changeESLayout(int width, int height);
    public native int drawESFrame();
    public native void setDisplaySurface(Surface surface);

    // ---- Callbacks
    public native void nativeStateFrameCallback(IFrameCallback cb);
    public native void nativeFrameCallback(IFrameCallback cb);
    public native void nativeAdasFrameCallback(IFrameCallback cb);
    public native void nativeEventCallback(IEventCallback cb);
    public native void nativeSetFrameCallback(int type, IFrameCallback cb);

    // ---- Comandos: nativeSendCmd(cmdFd, -1, comando, -1, -1, largo, datos)
    public native int nativeSendCmd(int cmdFd, int a, int cmd, int b, int c, int len, byte[] data);
    public native boolean nativeTakesnoshot(String path);

    // ---- Archivos de la tarjeta
    public native int nativeGetFileCount(int type, byte[] out);
    public native int nativeGetFile(int index, byte[] out, int len);
    public native boolean nativeDownloadFile(String src, String dst, ProgressCallback cb);

    // ---- ADAS (detección de carril de la librería)
    public native void nativeStartAdas(byte[] params);
    public native int setLanePara(int a, int b, int c, float d, int e, int f, int g, int h, int i);
    public native int getLanePara(int a, byte[] out);
    public native int setLanePos(int a, int b);
    public native int getLanePos(byte[] out);
    public native boolean GetUploadAdasDataStatus();
    public native void SetUploadAdasDataStatus(boolean b);
}
