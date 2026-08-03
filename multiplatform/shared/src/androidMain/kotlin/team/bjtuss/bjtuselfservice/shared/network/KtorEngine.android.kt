package team.bjtuss.bjtuselfservice.shared.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun schoolHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
