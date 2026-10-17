package tech.dm.conso.car

import androidx.car.app.HandshakeInfo
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.versioning.CarAppApiLevels
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardScreenTest {
    private val carContext = TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).apply {
        updateHandshakeInfo(HandshakeInfo("test.host", CarAppApiLevels.LEVEL_8))
    }

    @Test
    fun disconnectedDashboardOffersObdAndDemoTiles() {
        val template = (DashboardScreen(carContext).onGetTemplate() as MapWithContentTemplate).contentTemplate as GridTemplate

        assertEquals("EcoDrive OBD", template.title.toString())
        assertEquals(listOf("OBD", "Démo"), template.singleList!!.items.map { (it as GridItem).title.toString() })
    }
}
