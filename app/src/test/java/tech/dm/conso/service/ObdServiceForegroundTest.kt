package tech.dm.conso.service

import android.Manifest
import android.content.Intent
import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A Bluetooth auto-start must never take the location type away from a running trip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObdServiceForegroundTest {
    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    private val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun typesAreOnlyAddedWhileRunning() {
        val fromApp = ObdService.nextForegroundTypes(0, fineLocationGranted = true, fromBackground = false)
        assertEquals(device or location, fromApp)
        assertEquals(device or location, ObdService.nextForegroundTypes(fromApp, fineLocationGranted = true, fromBackground = true))

        val fromAuto = ObdService.nextForegroundTypes(0, fineLocationGranted = true, fromBackground = true)
        assertEquals(device, fromAuto)
        assertEquals(device or location, ObdService.nextForegroundTypes(fromAuto, fineLocationGranted = true, fromBackground = false))

        assertEquals(device, ObdService.nextForegroundTypes(device or location, fineLocationGranted = false, fromBackground = true))
    }

    @Test
    fun autoStartDuringAppTripKeepsLocation() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildService(ObdService::class.java, Intent(context, ObdService::class.java))
            .create().startCommand(0, 1)
        assertEquals(device or location, controller.get().foregroundServiceType)

        controller.withIntent(Intent(context, ObdService::class.java).setAction(ObdService.ACTION_AUTO)).startCommand(0, 2)
        assertEquals(device or location, controller.get().foregroundServiceType)
    }

    @Test
    fun autoStartAloneHasNoLocation() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildService(
            ObdService::class.java,
            Intent(context, ObdService::class.java).setAction(ObdService.ACTION_AUTO),
        ).create().startCommand(0, 1)
        assertEquals(device, controller.get().foregroundServiceType)
    }
}
