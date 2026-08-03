package team.bjtuss.bjtuselfservice.shared.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun schoolHttpEngineFactory(): HttpClientEngineFactory<*> = CIO
