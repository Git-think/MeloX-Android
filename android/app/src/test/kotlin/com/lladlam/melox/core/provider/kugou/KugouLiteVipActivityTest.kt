package com.lladlam.melox.core.provider.kugou

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class KugouLiteVipActivityTest {
    private val session = KugouSession(
        token = "test-lite-token", userId = 123L, vipToken = "", vipType = 0,
        dfid = "test-dfid", mid = "test-mid", guid = "test-guid", dev = "test-dev",
        mac = "", webGl = "", platform = KugouPlatform.Lite,
    )

    private fun api(body: String, loggedIn: Boolean = true, inspect: (Request) -> Unit = {}): KugouLiteApiClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        return KugouLiteApiClient(
            sessionProvider = { if (loggedIn) session else session.copy(token = "") },
            httpClient = client,
        )
    }

    @Test fun dailyClaimUsesLiteIdentityAndExplicitDate() = runBlocking {
        var count = 0
        api("""{"status":1}""") { request ->
            count++
            assertEquals("POST", request.method)
            assertEquals("/youth/v1/recharge/receive_vip_listen_song", request.url.encodedPath)
            assertEquals("90139", request.url.queryParameter("source_id"))
            assertEquals("2026-10-25", request.url.queryParameter("receive_day"))
            assertEquals(KugouPlatform.Lite.appId.toString(), request.url.queryParameter("appid"))
            assertEquals("test-lite-token", request.url.queryParameter("token"))
            assertFalse(request.url.queryParameter("signature").isNullOrBlank())
        }.claimDailyVip(LocalDate.of(2026, 10, 25))
        assertEquals(1, count)
    }

    @Test fun upgradeUsesCurrentLiteUser() = runBlocking {
        api("""{"status":1}""") { request ->
            assertEquals("/youth/v1/listen_song/upgrade_vip_reward", request.url.encodedPath)
            assertEquals("123", request.url.queryParameter("kugouid"))
            assertEquals("1", request.url.queryParameter("ad_type"))
        }.upgradeDailyVip()
        Unit
    }

    @Test fun alreadyClaimedAndBlockedRetainDistinctCodes() {
        for (code in listOf(131001, 20028)) {
            val error = assertThrows(KugouApiException::class.java) {
                runBlocking { api("""{"status":0,"error_code":$code,"error_msg":"activity rejected"}""").claimDailyVip() }
            }
            assertEquals(code, error.errorCode)
        }
    }

    @Test fun missingSuccessStatusIsNotReportedAsClaimed() {
        assertThrows(IOException::class.java) { runBlocking { api("{}").claimDailyVip() } }
        assertThrows(IOException::class.java) { runBlocking { api("{}").upgradeDailyVip() } }
    }

    @Test fun signedOutAccountNeverSendsARewardRequest() {
        val api = api("{}", loggedIn = false) { fail("Signed-out reward request must not be sent") }
        assertThrows(KugouAuthException::class.java) { runBlocking { api.claimDailyVip() } }
        assertThrows(KugouAuthException::class.java) { runBlocking { api.upgradeDailyVip() } }
    }
}
