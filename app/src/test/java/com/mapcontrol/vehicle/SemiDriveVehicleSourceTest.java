package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SemiDriveVehicleSourceTest {

    @Test
    public void onlyCherySemiDriveDevicesAreDetected() {
        assertTrue(VehiclePlatform.isSemiDriveDevice("chery_t1e"));
        assertTrue(VehiclePlatform.isSemiDriveDevice("chery_t1d"));
        assertTrue(VehiclePlatform.isSemiDriveDevice("chery_t19c"));
        assertFalse(VehiclePlatform.isSemiDriveDevice("msmnile_gvmq"));
        assertFalse(VehiclePlatform.isSemiDriveDevice(null));
    }

    @Test
    public void keyStatusMapsToDesaySvPowerMode() {
        int key = SemiDriveVehicleSource.KEY_STS;
        assertEquals(ReadOnlyID.ID_SYSTEM_POWER_MODE, SemiDriveVehicleSource.readOnlyIdFor(key));
        assertEquals(0, SemiDriveVehicleSource.toDesaySvValue(key, 0));
        assertEquals(1, SemiDriveVehicleSource.toDesaySvValue(key, 1));
        assertEquals(2, SemiDriveVehicleSource.toDesaySvValue(key, 2));
        assertEquals(2, SemiDriveVehicleSource.toDesaySvValue(key, 3));
    }

    @Test
    public void engineSpeedIsStoredInTensLikeDesaySv() {
        int rpm = SemiDriveVehicleSource.ENGINE_SPEED;
        assertEquals(ReadOnlyID.ID_ENGINE_RPM, SemiDriveVehicleSource.readOnlyIdFor(rpm));
        assertEquals(78, SemiDriveVehicleSource.toDesaySvValue(rpm, 780));
    }

    @Test
    public void gearAndBodyValuesPassThrough() {
        assertEquals(ReadOnlyID.ID_GEARBOX_STATE,
                SemiDriveVehicleSource.readOnlyIdFor(SemiDriveVehicleSource.GEAR_POSITION));
        assertEquals(2, SemiDriveVehicleSource.toDesaySvValue(SemiDriveVehicleSource.GEAR_POSITION, 2));
        assertEquals(ReadOnlyID.ID_TRUNK_STATE,
                SemiDriveVehicleSource.readOnlyIdFor(SemiDriveVehicleSource.TRUNK_STS));
        assertEquals(1, SemiDriveVehicleSource.toDesaySvValue(SemiDriveVehicleSource.TRUNK_STS, 1));
    }

    @Test
    public void unknownPropertyHasNoDesaySvId() {
        assertEquals(-1, SemiDriveVehicleSource.readOnlyIdFor(12345));
    }
}
