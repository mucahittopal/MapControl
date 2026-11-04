package com.mapcontrol.vehicle;

import android.os.Build;

/**
 * Ana ünite ailesi. MapControl DesaySV (Qualcomm 8155) üniteler için yazıldı. 2023-2024 Chery
 * Tiggo 7 Pro, Tiggo 8 Pro ve Omoda 5 (1.6 TGDI, 183 HP) ise SemiDrive X9 ünite (Android 10)
 * kullanır: araç verisi DesaySV CarInfo/VDBus yerine Android araç özelliklerinden gelir.
 */
public final class VehiclePlatform {

    private static final boolean SEMIDRIVE = isSemiDriveDevice(Build.DEVICE);

    private VehiclePlatform() {
    }

    /** SemiDrive X9 üniteli Chery ({@code ro.product.device} chery_t1e / chery_t1d / chery_t19c). */
    public static boolean isSemiDrive() {
        return SEMIDRIVE;
    }

    static boolean isSemiDriveDevice(String device) {
        return "chery_t1e".equals(device)
                || "chery_t1d".equals(device)
                || "chery_t19c".equals(device);
    }
}
