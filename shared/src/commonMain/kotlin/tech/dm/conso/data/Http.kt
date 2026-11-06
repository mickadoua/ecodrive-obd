package tech.dm.conso.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

internal val http by lazy {
    HttpClient {
        install(HttpTimeout)
        defaultRequest { headers.append(HttpHeaders.UserAgent, "EcoDriveOBD/0.1 (+https://github.com/mickadoua/ecodrive-obd)") }
    }
}
