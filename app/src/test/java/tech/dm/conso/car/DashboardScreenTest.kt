package tech.dm.conso.car

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardScreenTest {
    private val carContext = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())

    @Test
    fun disconnectedDashboardShowsMetricsAndStartActions() {
        val template = DashboardScreen(carContext).onGetTemplate() as PaneTemplate

        assertEquals("Conso", template.title.toString())
        assertEquals(
            listOf("Instantané", "Moyenne trajet", "Éco-score –", "Pente"),
            template.pane.rows.map { it.title.toString() },
        )
        assertEquals(listOf("OBD", "Démo"), template.pane.actions.map { it.title.toString() })
    }
}
