package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import com.mapcontrol.vehicle.VehiclePlatform;

/**
 * SemiDrive Chery ünitesinde aracın sol dock'u (235 px) ve sağ durum çubuğu (100 px), uygulama penceresinin üstünde
 * duran ayrı panellerdir ve MapControl'ün sol menüsünü kapatır. Aracın SystemUI'si bu çubukları Android Auto ve
 * CarPlay için kendisi gizler; aynı işlevi dışa açık, izinsiz bir binder ile sunar
 * ({@code com.chery.window.IWindowUiVisibility}). MapControl öndeyken çubuklar gizlenir, arka plana geçince geri açılır.
 */
public final class SemiDriveSystemBars {

    private static final String TAG = "SemiDriveSystemBars";
    private static final String DESCRIPTOR = "com.chery.window.IWindowUiVisibility";
    private static final int TX_SET_UI_VISIBILITY = 2;
    private static final int FLAG_BOTH = 3; // 1 sol dock, 2 sağ çubuk
    private static final int VIS_HIDE = 0;
    private static final int VIS_SHOW = 2;
    /** SystemUI öne gelen her görevde çubukları kendisi geri açar; öne gelişten sonra birkaç kez yeniden gizlenir. */
    private static final long[] REHIDE_DELAYS_MS = {20, 100, 300, 800, 2000};
    private static final String PREFS = "semidrive_system_bars";
    private static final String KEY_HIDDEN_BY_US = "hidden";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object REHIDE_TOKEN = new Object();

    private static Context appContext;
    private static IBinder binder;
    private static boolean wantHidden;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                if (service == null || !DESCRIPTOR.equals(service.getInterfaceDescriptor())) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
            binder = service;
            send(wantHidden);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };

    private SemiDriveSystemBars() {
    }

    /**
     * Süreç açılışında çağrılır: önceki süreç çubukları gizli bırakıp öldüyse geri açar. Yalnız bizim gizlediğimiz
     * durumda gönderilir; Android Auto öndeyken SystemUI'nin kendi gizlemesine dokunulmaz.
     */
    public static void restoreIfLeftHidden(Context context) {
        if (!VehiclePlatform.isSemiDrive() || !prefs(context).getBoolean(KEY_HIDDEN_BY_US, false)) {
            return;
        }
        setHidden(context, false);
    }

    public static void setHidden(Context context, boolean hidden) {
        if (!VehiclePlatform.isSemiDrive()) {
            return;
        }
        bind(context);
        wantHidden = hidden;
        MAIN.removeCallbacksAndMessages(REHIDE_TOKEN);
        send(hidden);
        if (hidden) {
            for (long delay : REHIDE_DELAYS_MS) {
                MAIN.postAtTime(() -> send(true), REHIDE_TOKEN,
                        android.os.SystemClock.uptimeMillis() + delay);
            }
        }
    }

    private static void bind(Context context) {
        if (appContext != null) {
            return;
        }
        appContext = context.getApplicationContext();
        // BIND_AUTO_CREATE yok: SystemUI zaten çalışıyor; yoksa (başka ünite) bağlantı kurulmaz.
        Intent intent = new Intent("com.android.systemui.action.uivisibility")
                .setComponent(new ComponentName("com.android.systemui",
                        "com.android.systemui.SystemUIService"));
        try {
            if (!appContext.bindService(intent, CONNECTION, 0)) {
                Log.w(TAG, "SystemUI çubuk servisi yok");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SystemUI çubuk servisine bağlanılamadı: " + e);
        }
    }

    private static void send(boolean hide) {
        IBinder b = binder;
        if (b == null) {
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(FLAG_BOTH);
            data.writeInt(hide ? VIS_HIDE : VIS_SHOW);
            data.writeString("mapcontrol");
            data.writeString(appContext.getPackageName());
            b.transact(TX_SET_UI_VISIBILITY, data, reply, 0);
            reply.readException();
            prefs(appContext).edit().putBoolean(KEY_HIDDEN_BY_US, hide).apply();
        } catch (Exception e) {
            // SystemUI yeniden başlıyor: bağlantı geri gelince son istek uygulanır.
            binder = null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
