package com.lladlam.melox.miplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wire adapter for the exported Xiaomi audio client, not the signature-only CoreService. */
public final class MiPlayTransport implements AutoCloseable {
    public static final String PACKAGE = "com.milink.service";
    static final String DESCRIPTOR = "com.xiaomi.miplay.audioclient.IMiPlayClient";
    static final String CALLBACK = "com.xiaomi.miplay.audioclient.IMiPlayClientCallback";
    public interface Listener {
        void onReady();
        void onDevice(String id, String name, String room);
        void onLost(String id);
        void onConnection(String id, int state);
        void onVolume(String id, double volume);
        void onError(String message);
    }
    private interface Writer { void write(Parcel parcel); }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile IBinder remote;
    private volatile boolean closed;
    private boolean bound;

    public MiPlayTransport(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    private void post(Runnable action) { main.post(() -> { if (!closed) action.run(); }); }
    private final IBinder.DeathRecipient death = this::serviceDied;
    private void serviceDied() {
        remote = null;
        post(() -> listener.onError("小米妙播服务已断开"));
    }
    private final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
            if (code < 1 || code > 60) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            try {
                switch (code) {
                    case 1: post(() -> listener.onReady()); break;
                    case 2: post(() -> listener.onError("小米妙播初始化失败，请先完成系统互联服务的初始化")); break;
                    case 3: case 4: case 21: case 22:
                        if (data.readInt() != 0) {
                            // Both device parcel layouts share this prefix. Consume only the fields we use.
                            String id = data.readString();
                            String name = data.readString();
                            data.readInt(); // isGroup
                            data.readString(); data.readString();
                            if (code == 21 || code == 22) data.readString(); // control-center mac
                            else data.readString(); // legacy p2pMac
                            String room = data.readString();
                            if (id != null && !id.isEmpty()) post(() -> listener.onDevice(id, name == null ? "MiPlay" : name, room));
                        }
                        break;
                    case 5: {
                        String id = data.readString(); post(() -> listener.onLost(id)); break;
                    }
                    case 7: {
                        String id = data.readString(); int state = data.readInt();
                        post(() -> listener.onConnection(id, state)); break;
                    }
                    case 13: case 14: {
                        Map<?, ?> values = data.readHashMap(getClass().getClassLoader());
                        if (values != null) for (Map.Entry<?, ?> entry : values.entrySet()) {
                            if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                                String id = (String) entry.getKey(); double value = ((Number) entry.getValue()).doubleValue();
                                post(() -> listener.onVolume(id, value));
                            }
                        }
                        break;
                    }
                    case 34: post(() -> listener.onError("小米妙播底层服务已断开")); break;
                    default: break; // Acknowledge optional callbacks without loading Xiaomi parcelable classes.
                }
                if (reply != null) reply.writeNoException();
            } catch (RuntimeException error) {
                post(() -> listener.onError("小米妙播回调格式不兼容：" + error.getClass().getSimpleName()));
                if (reply != null) reply.writeException(error);
            }
            return true;
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = binder;
            submit(() -> {
                if (!DESCRIPTOR.equals(binder.getInterfaceDescriptor())) throw new IllegalStateException("未知的小米妙播接口");
                binder.linkToDeath(death, 0);
                call(1, data -> {
                    data.writeString(context.getPackageName());
                    data.writeStrongBinder(callback);
                    data.writeString("");
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { death.binderDied(); }
        @Override public void onNullBinding(ComponentName name) { post(() -> listener.onError("小米妙播服务未提供客户端接口")); }
        @Override public void onBindingDied(ComponentName name) { death.binderDied(); }
    };
    private interface Task { void run() throws Exception; }
    private synchronized void submit(Task task) {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            try { task.run(); }
            catch (Exception error) { post(() -> listener.onError(error.getMessage() == null ? error.toString() : error.getMessage())); }
        });
    }
    private int call(int code, Writer writer) throws RemoteException {
        IBinder binder = remote;
        if (binder == null) throw new RemoteException("小米妙播服务未连接");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); writer.write(data);
            if (!binder.transact(code, data, reply, 0)) throw new RemoteException("小米妙播不支持调用 " + code);
            reply.readException();
            int result = reply.readInt();
            if (result < 0) throw new RemoteException("小米妙播调用失败（" + code + ": " + result + "）");
            return result;
        } finally { reply.recycle(); data.recycle(); }
    }
    public synchronized void bind() {
        if (bound || closed) return;
        Intent intent = new Intent("com.xiaomi.miplay.action.MIPLAY_AUDIO_CLIENT_SERVICE")
            .setComponent(new ComponentName(PACKAGE, "com.xiaomi.miplay.mylibrary.MiPlayAudioService"));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) listener.onError("未找到可用的小米妙播音频服务");
        } catch (RuntimeException error) { listener.onError(error.toString()); }
    }
    public void scan(boolean enabled) { submit(() -> call(enabled ? 3 : 4, data -> { if (enabled) data.writeInt(2); })); }
    /** Mirrors the active local audio through Xiaomi's system capture/transport implementation. */
    public void play(String id) { submit(() -> call(6, data -> { data.writeStringArray(new String[]{id}); data.writeString(""); })); }
    public void stop(String id) { submit(() -> call(7, data -> data.writeStringArray(new String[]{id}))); }
    public void volume(String id, double volume) { submit(() -> call(17, data -> { data.writeStringArray(new String[]{id}); data.writeInt((int) Math.max(0, Math.min(100, volume))); })); }
    public void queryVolume(String id) { submit(() -> call(18, data -> data.writeStringArray(new String[]{id}))); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> {
            IBinder binder = remote;
            if (binder != null) {
                try { call(2, data -> data.writeString(context.getPackageName())); } catch (Exception ignored) { }
                try { binder.unlinkToDeath(death, 0); } catch (RuntimeException ignored) { }
            }
            remote = null;
        });
        worker.shutdown();
        if (bound) {
            try { context.unbindService(connection); }
            catch (RuntimeException ignored) { /* The ROM may already have removed the binding. */ }
            bound = false;
        }
    }
}
