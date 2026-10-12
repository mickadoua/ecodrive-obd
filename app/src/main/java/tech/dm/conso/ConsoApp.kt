package tech.dm.conso

import android.app.Application
import org.osmdroid.config.Configuration
import tech.dm.conso.data.TelemetryRepository

class ConsoApp : Application() {
    lateinit var telemetry: TelemetryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
        telemetry = TelemetryRepository(this)
    }

    companion object {
        lateinit var instance: ConsoApp
            private set
    }
}
