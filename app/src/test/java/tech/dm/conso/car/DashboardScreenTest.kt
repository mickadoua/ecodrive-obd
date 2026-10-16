package tech.dm.conso.car

import androidx.car.app.HandshakeInfo
import androidx.car.app.model.PaneTemplate
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
        updateHandshakeInfo(HandshakeInfo("test.host", CarAppApiLevels.LEVEL_4))
    }

    @Test
    fun disconnectedDashboardShowsMetricsAndStartActions() {
        val template = DashboardScreen(carContext).onGetTemplate() as PaneTemplate

        assertEquals("Conso", template.title.toString())
        assertEquals(
            listOf("Consommation", "Conseil", "Éco-score", "Trajet"),
            template.pane.rows.map { it.title.toString() },
        )
        assertEquals(listOf("OBD", "Démo"), template.pane.actions.map { it.title.toString() })
    }
}
