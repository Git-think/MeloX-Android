package com.lladlam.melox.miplay;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Parcel fixtures recovered from the installed Xiaomi client's Stub and Parcelable layouts. */
public final class MiPlayWireRegression {
    private interface Writer { void write(Parcel data); }
    private static void callback(IBinder binder, int code, Writer writer) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MiPlayTransport.CALLBACK); writer.write(data);
            if (!binder.transact(code, data, reply, 0)) throw new AssertionError("callback rejected");
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }
    public static void run(Context context) throws Exception {
        CountDownLatch callbacks = new CountDownLatch(4), commands = new CountDownLatch(2);
        AtomicReference<String> failure = new AtomicReference<>();
        MiPlayTransport transport = new MiPlayTransport(context, new MiPlayTransport.Listener() {
            @Override public void onReady() { }
            @Override public void onDevice(String id, String name, String room) {
                if (!"fixture".equals(id) || !"音箱 & TV".equals(name) || !"客厅".equals(room)) failure.set("Device parcel misaligned");
                callbacks.countDown();
            }
            @Override public void onLost(String id) { }
            @Override public void onConnection(String id, int state) {
                if (!"fixture".equals(id) || state != 1) failure.set("Connection parcel misaligned");
                callbacks.countDown();
            }
            @Override public void onVolume(String id, double value) {
                if (!"fixture".equals(id) || value != 55.0) failure.set("Volume parcel misaligned");
                callbacks.countDown();
            }
            @Override public void onError(String message) { failure.set(message); }
        });
        try {
            Field callbackField = MiPlayTransport.class.getDeclaredField("callback"); callbackField.setAccessible(true);
            IBinder callback = (IBinder) callbackField.get(transport);
            for (int code : new int[]{3, 21}) callback(callback, code, data -> {
                data.writeInt(1); data.writeString("fixture"); data.writeString("音箱 & TV"); data.writeInt(0);
                data.writeString("first"); data.writeString("second"); data.writeString("third"); data.writeString("客厅");
            });
            callback(callback, 7, data -> { data.writeString("fixture"); data.writeInt(1); });
            callback(callback, 14, data -> {
                HashMap<String, Double> values = new HashMap<>(); values.put("fixture", 55.0); data.writeMap(values);
            });
            // Optional custom parcelables must be acknowledged without class-loading their contents.
            callback(callback, 10, data -> data.writeString("ignored optional metadata"));
            if (!callbacks.await(3, TimeUnit.SECONDS)) throw new AssertionError("Callbacks did not reach the main thread");
            Field remote = MiPlayTransport.class.getDeclaredField("remote"); remote.setAccessible(true);
            remote.set(transport, new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    data.enforceInterface(MiPlayTransport.DESCRIPTOR);
                    if (code == 6 || code == 17) {
                        String[] ids = data.createStringArray();
                        if (ids == null || ids.length != 1 || !"fixture".equals(ids[0])) failure.set("Wrong target device");
                        if (code == 6 && !"".equals(data.readString())) failure.set("Wrong mirror parameter");
                        if (code == 17 && data.readInt() != 55) failure.set("Wrong volume encoding");
                        commands.countDown();
                    }
                    reply.writeNoException(); reply.writeInt(0); return true;
                }
            });
            transport.play("fixture"); transport.volume("fixture", 55);
            if (!commands.await(3, TimeUnit.SECONDS)) throw new AssertionError("Commands did not reach Binder");
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { transport.close(); }
    }
}
