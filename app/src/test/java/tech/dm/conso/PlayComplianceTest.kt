package tech.dm.conso

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.dm.conso.service.ObdService

/** Guards the manifest choices made for the first Play Store release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayComplianceTest {
    private val context = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    private fun packageInfo(flags: Int) = context.packageManager.getPackageInfo(context.packageName, flags)

    @Test
    fun noBackgroundLocationNorAndroidAutoPermission() {
        val requested = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertFalse(requested.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertTrue(requested.none { it.startsWith("androidx.car.app.") })
        assertTrue(requested.contains("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun noAndroidAutoServiceNorMetadata() {
        val services = packageInfo(PackageManager.GET_SERVICES).services.orEmpty().map { it.name }
        assertEquals(listOf(ObdService::class.java.name), services)
        @Suppress("DEPRECATION")
        val meta = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertFalse(meta?.containsKey("com.google.android.gms.car.application") ?: false)
        assertFalse(meta?.containsKey("androidx.car.app.minCarApiLevel") ?: false)
    }

    @Test
    fun autoStartedServiceNeverUsesLocation() {
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        assertEquals(device, ObdService.foregroundTypes(fineLocationGranted = true, fromBackground = true))
        assertEquals(device or location, ObdService.foregroundTypes(fineLocationGranted = true, fromBackground = false))
        assertEquals(device, ObdService.foregroundTypes(fineLocationGranted = false, fromBackground = false))
    }
}
