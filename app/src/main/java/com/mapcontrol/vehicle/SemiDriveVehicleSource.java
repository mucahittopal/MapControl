package com.mapcontrol.vehicle;

import android.content.Context;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * SemiDrive üniteli Chery araçların ({@link VehiclePlatform#isSemiDrive()}) araç verisi.
 * Chery'nin araç özelliklerini CarPropertyManager'dan okur ve {@link VehicleMetricsCache}'e DesaySV
 * {@link ReadOnlyID} anahtarları ve kodlamasıyla yazar; ekranlar ve yolculuk kaydı değişmeden çalışır.
 * <p>
 * Bu özellikler {@code android.car.permission.CAR_VENDOR_EXTENSION} (signature|privileged) ister:
 * uygulama /system/priv-app'e kurulmazsa okuma izin hatası verir ve bağlantı yok sayılır (README).
 * android.car sınıfları araçta önyükleme yolundadır; derleme bağımlılığı olmasın diye yansımayla kullanılır.
 */
final class SemiDriveVehicleSource {

    private static final String TAG = "SemiDriveVehicle";

    // Chery vendor özellikleri (Tiggo 7 Pro, chery_t1e).
    static final int KEY_STS = 557850624;                  // 0 kapalı, 1 ACC, 2 kontak açık, 3 marş
    static final int GEAR_POSITION = 557850630;            // 1 P, 2 R, 3 N, 4+ D
    static final int VEHICLE_SPEED_VSOSIG = 559947811;     // km/saat (Float)
    static final int VEHICLE_SPEED_VALIDATION = 557850660; // 0 = hız geçerli
    static final int ENGINE_SPEED = 557850661;             // devir/dakika
    static final int ODO_METRE_KM = 557850662;
    static final int RESIDUAL_ODO_METRE = 557850664;       // kalan menzil, km
    static final int IGNITION_DISTANCE = 557850679;        // kontaktan beri, km
    static final int DRIVER_DOOR_STS = 557850642;          // kapı ve bagaj: 0 kapalı, 1 açık
    static final int PSG_DOOR_STS = 557850643;
    static final int LHR_DOOR_STS = 557850644;
    static final int RHR_DOOR_STS = 557850645;
    static final int TRUNK_STS = 557850646;

    private static final int[] INT_PROPERTIES = {
            KEY_STS, GEAR_POSITION, VEHICLE_SPEED_VALIDATION, ENGINE_SPEED, ODO_METRE_KM,
            RESIDUAL_ODO_METRE, IGNITION_DISTANCE, DRIVER_DOOR_STS, PSG_DOOR_STS, LHR_DOOR_STS,
            RHR_DOOR_STS, TRUNK_STS,
    };

    private static final int MODULE = VehicleMetricsCache.MODULE_READONLY;

    private Object propertyManager;
    private Method getProperty;
    private Object callback;
    private volatile boolean connected;
    private volatile boolean speedValid = true;
    private volatile Float lastSpeed;

    boolean isConnected() {
        return connected;
    }

    /** Araç servisine bağlanır, tüm değerleri okur ve değişimlere abone olur. */
    void start(Context context, VehicleMetricsCache cache, Runnable onChanged) {
        if (!connect(context)) {
            return;
        }
        readAll(cache);
        subscribe(cache, onChanged);
    }

    void readAll(VehicleMetricsCache cache) {
        if (!connected) {
            return;
        }
        for (int id : INT_PROPERTIES) {
            Object value = read(Integer.class, id);
            if (value != null) {
                apply(cache, id, value);
            }
        }
        Object speed = read(Float.class, VEHICLE_SPEED_VSOSIG);
        if (speed != null) {
            apply(cache, VEHICLE_SPEED_VSOSIG, speed);
        }
    }

    void stop() {
        if (propertyManager == null || callback == null) {
            return;
        }
        try {
            Class<?> callbackClass = callbackClass();
            propertyManager.getClass().getMethod("unregisterCallback", callbackClass)
                    .invoke(propertyManager, callback);
        } catch (Throwable e) {
            Log.w(TAG, "unregisterCallback: " + e);
        }
        callback = null;
    }

    private boolean connect(Context context) {
        if (connected) {
            return true;
        }
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            Object car = carClass.getMethod("createCar", Context.class)
                    .invoke(null, context.getApplicationContext());
            Object manager = carClass.getMethod("getCarManager", String.class).invoke(car, "property");
            if (manager == null) {
                return false;
            }
            Method get = manager.getClass().getMethod("getProperty", Class.class, int.class, int.class);
            // İzin denetimi: CAR_VENDOR_EXTENSION yoksa ilk okuma SecurityException verir.
            get.invoke(manager, Integer.class, KEY_STS, 0);
            propertyManager = manager;
            getProperty = get;
            connected = true;
            return true;
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Araç özellikleri okunamıyor (priv-app kurulumu gerekir): " + e.getCause());
        } catch (Throwable e) {
            Log.w(TAG, "Araç servisi yok: " + e);
        }
        return false;
    }

    private Object read(Class<?> type, int propertyId) {
        try {
            Object value = getProperty.invoke(propertyManager, type, propertyId, 0);
            return value == null ? null : value.getClass().getMethod("getValue").invoke(value);
        } catch (Throwable e) {
            return null;
        }
    }

    private void subscribe(VehicleMetricsCache cache, Runnable onChanged) {
        try {
            Class<?> callbackClass = callbackClass();
            Object cb = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "onChangeEvent":
                                onChangeEvent(cache, args[0], onChanged);
                                return null;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return TAG;
                            default:
                                return null;
                        }
                    });
            Method register = propertyManager.getClass()
                    .getMethod("registerCallback", callbackClass, int.class, float.class);
            for (int id : INT_PROPERTIES) {
                register.invoke(propertyManager, cb, id, 0f);
            }
            register.invoke(propertyManager, cb, VEHICLE_SPEED_VSOSIG, 1f);
            callback = cb;
        } catch (Throwable e) {
            Log.w(TAG, "registerCallback: " + e);
        }
    }

    private void onChangeEvent(VehicleMetricsCache cache, Object event, Runnable onChanged) {
        try {
            int id = (Integer) event.getClass().getMethod("getPropertyId").invoke(event);
            Object value = event.getClass().getMethod("getValue").invoke(event);
            if (value != null && apply(cache, id, value)) {
                onChanged.run();
            }
        } catch (Throwable e) {
            Log.w(TAG, "onChangeEvent: " + e);
        }
    }

    /** Chery değerini DesaySV karşılığına çevirip önbelleğe yazar; değer değiştiyse true. */
    private boolean apply(VehicleMetricsCache cache, int propertyId, Object value) {
        if (propertyId == VEHICLE_SPEED_VSOSIG) {
            lastSpeed = ((Number) value).floatValue();
            return updateSpeed(cache);
        }
        if (propertyId == VEHICLE_SPEED_VALIDATION) {
            speedValid = ((Number) value).intValue() == 0;
            return updateSpeed(cache);
        }
        int readOnlyId = readOnlyIdFor(propertyId);
        if (readOnlyId < 0) {
            return false;
        }
        return cache.update(MODULE, readOnlyId,
                new int[]{toDesaySvValue(propertyId, ((Number) value).intValue())});
    }

    private boolean updateSpeed(VehicleMetricsCache cache) {
        Float speed = lastSpeed;
        int value = speedValid && speed != null ? Math.round(speed) : VehicleMetricsCache.INVALID;
        return cache.update(MODULE, ReadOnlyID.ID_CAR_SPEED, new int[]{value});
    }

    /** Chery özelliğinin DesaySV {@link ReadOnlyID} karşılığı; karşılığı yoksa -1. */
    static int readOnlyIdFor(int propertyId) {
        switch (propertyId) {
            case KEY_STS:
                return ReadOnlyID.ID_SYSTEM_POWER_MODE;
            case GEAR_POSITION:
                return ReadOnlyID.ID_GEARBOX_STATE;
            case ENGINE_SPEED:
                return ReadOnlyID.ID_ENGINE_RPM;
            case ODO_METRE_KM:
                return ReadOnlyID.ID_GRAND_TOTAL_KM;
            case RESIDUAL_ODO_METRE:
                return ReadOnlyID.ID_ENDURANCE_KM;
            case IGNITION_DISTANCE:
                return ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING;
            case DRIVER_DOOR_STS:
                return ReadOnlyID.ID_DRIVE_DOOR_STATE;
            case PSG_DOOR_STS:
                return ReadOnlyID.ID_PASSENGER_DOOR_STATE;
            case LHR_DOOR_STS:
                return ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE;
            case RHR_DOOR_STS:
                return ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE;
            case TRUNK_STS:
                return ReadOnlyID.ID_TRUNK_STATE;
            default:
                return -1;
        }
    }

    /** Chery değerini DesaySV kodlamasına çevirir (vites, kapı ve km değerleri aynıdır). */
    static int toDesaySvValue(int propertyId, int value) {
        switch (propertyId) {
            case KEY_STS:
                // DesaySV güç modu: 0 kapalı, 1 hazır, 2 çalışıyor. Chery: 1 ACC, 2 kontak açık, 3 marş.
                return value >= 2 ? 2 : value;
            case ENGINE_SPEED:
                // DesaySV devri onda bir verir (formatter 10 ile çarpar).
                return Math.round(value / 10f);
            default:
                return value;
        }
    }

    private static Class<?> callbackClass() throws ClassNotFoundException {
        return Class.forName("android.car.hardware.property.CarPropertyManager$CarPropertyEventCallback");
    }
}
