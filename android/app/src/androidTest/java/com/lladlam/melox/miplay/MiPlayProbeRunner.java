package com.lladlam.melox.miplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Discovery-only probe: never selects an output or starts playback on the user's devices. */
public final class MiPlayProbeRunner extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        CountDownLatch initialized = new CountDownLatch(1);
        AtomicInteger devices = new AtomicInteger();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<MiPlayTransport> transport = new AtomicReference<>();
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        try {
            MiPlayWireRegression.run(getTargetContext());
            result.putBoolean("privateWireRegression", true);
            runOnMainSync(() -> {
                transport.set(new MiPlayTransport(getTargetContext(), new MiPlayTransport.Listener() {
                    @Override public void onReady() { ready.set(true); initialized.countDown(); transport.get().scan(true); }
                    @Override public void onDevice(String id, String name, String room) { devices.incrementAndGet(); }
                    @Override public void onLost(String id) { }
                    @Override public void onConnection(String id, int state) { }
                    @Override public void onVolume(String id, double volume) { }
                    @Override public void onError(String message) { error.set(message); initialized.countDown(); }
                }));
                transport.get().bind();
            });
            boolean completed = initialized.await(15, TimeUnit.SECONDS);
            if (ready.get()) Thread.sleep(8000);
            result.putBoolean("privateClientInitialized", ready.get());
            result.putInt("deviceCallbacks", devices.get());
            if (!completed) result.putString("error", "Timed out waiting for Xiaomi init callback");
            else if (error.get() != null) result.putString("error", error.get());
            finish(completed && ready.get() && error.get() == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        } catch (Exception failure) {
            result.putString("error", failure.toString()); finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (transport.get() != null) runOnMainSync(() -> transport.get().close());
        }
    }
}
