package team.bjtuss.bjtuselfservice.shared.data.home

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HomeStatusRemoteDataSourceTest {
    @Test fun requestsExactMisEndpointAndParsesStatus() {
        runBlocking {
            val transport = FakeTransport(okResponse())
            val status = SchoolHomeStatusRemoteDataSource(transport).fetch()
            assertEquals("https://mis.bjtu.edu.cn/osys_ajax_wrap/", transport.request?.url)
            assertEquals("1", status.newMailCount)
        }
    }

    @Test fun casRedirectIsSessionExpired() {
        runBlocking {
            val error = assertFailsWith<HomeStatusRemoteException> {
                SchoolHomeStatusRemoteDataSource(
                    FakeTransport(okResponse().copy(finalUrl = "https://cas.bjtu.edu.cn/auth/login/")),
                ).fetch()
            }
            assertEquals(HomeStatusFailure.SESSION_EXPIRED, error.reason)
        }
    }

    @Test fun malformedBodyIsParseFailure() {
        runBlocking {
            val error = assertFailsWith<HomeStatusRemoteException> {
                SchoolHomeStatusRemoteDataSource(FakeTransport(okResponse().copy(body = "{}".encodeToByteArray()))).fetch()
            }
            assertEquals(HomeStatusFailure.PARSE, error.reason)
        }
    }
}

private class FakeTransport(private val response: SchoolHttpResponse) : SchoolHttpTransport {
    var request: SchoolHttpRequest? = null
    override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
        this.request = request
        return response
    }
    override fun clearSession() = Unit
}

private fun okResponse() = SchoolHttpResponse(
    statusCode = 200,
    finalUrl = "https://mis.bjtu.edu.cn/osys_ajax_wrap/",
    body = """{"net_fee":"9","ecard_yuer":"30","newmail_count":"1"}""".encodeToByteArray(),
)
