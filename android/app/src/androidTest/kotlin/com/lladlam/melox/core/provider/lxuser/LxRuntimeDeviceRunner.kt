package com.lladlam.melox.core.provider.lxuser

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Run with -PmeloxTestInstrumentationRunner=...LxRuntimeDeviceRunner. */
class LxRuntimeDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            runRegression()
            result.putString("result", "QuickJS reuse, serialized calls and delayed HTTP callback passed")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("error", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun runRegression() {
        val script = """
            globalThis.lx.on(globalThis.lx.EVENT_NAMES.request, ({info}) =>
              new Promise((resolve, reject) => {
                globalThis.lx.request(info.musicInfo.url, {method:'GET', timeout:5000}, (err, response, body) => {
                  if (err) reject(err); else resolve(body.url + '?id=' + info.musicInfo.id)
                })
              }))
            globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited, {
              sources: {tx: {qualitys:['128k'], actions:['musicUrl']}}
            })
        """.trimIndent()
        val id = "device-thread-regression"
        val clients = Executors.newFixedThreadPool(2)
        val requestsSeen = AtomicInteger()
        val fixture = OkHttpClient.Builder().addInterceptor { chain ->
            // Regression: previous drain stopped after about two seconds.
            if (requestsSeen.incrementAndGet() == 1) Thread.sleep(2_300L)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"url":"https://example.invalid/audio"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val runtime = LxUserRuntime(fixture)
        try {
            runtime.load(LxUserScript(script))
            val requests = (1..2).map { index ->
                clients.submit<Any?> {
                    check(runtime.supportsSource("tx"))
                    runtime.callAction("musicUrl", mapOf(
                        "source" to "tx", "type" to "128k",
                        "musicInfo" to mapOf("id" to index, "url" to "https://example.invalid/api"),
                    ))
                }
            }
            requests.forEachIndexed { index, request ->
                check(request.get(15, TimeUnit.SECONDS) == "https://example.invalid/audio?id=${index + 1}")
            }
            check(requestsSeen.get() == 2)
            runtime.close()
            check(runCatching { runtime.callAction("musicUrl", emptyMap()) }.isFailure)

            val cached = LxUserRuntime.session(id, script)
            check(LxUserRuntime.session(id, script) === cached)
            LxUserRuntime.evict(id)
            val replacement = LxUserRuntime.session(id, script)
            check(replacement !== cached && replacement.supportsSource("tx"))
        } finally {
            runtime.close()
            LxUserRuntime.evict(id)
            clients.shutdownNow()
        }
    }
}
