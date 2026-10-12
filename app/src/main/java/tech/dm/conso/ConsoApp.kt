package tech.dm.conso

import android.app.Application
import tech.dm.conso.data.TelemetryRepository

class ConsoApp : Application() {
    lateinit var telemetry: TelemetryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        telemetry = TelemetryRepository(this)
    }

    companion object {
        lateinit var instance: ConsoApp
            private set
    }
}
