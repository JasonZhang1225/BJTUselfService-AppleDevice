package team.bjtuss.bjtuselfservice.shared.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun schoolHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin
