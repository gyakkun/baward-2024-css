package moe.nyamori.test

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.util.NaiveRateLimit
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val logger = LoggerFactory.getLogger("MainKt")
    val virtThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()
    val jdkHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .executor(virtThreadExecutor)
        .build()
    val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0"
    val cssUrl = "https://bgm.tv/css/award_2024.css"
    val app = Javalin.create { config ->
        config.useVirtualThreads = true
        config.router.apiBuilder {
            get("/health") { ctx ->
                NaiveRateLimit.requestPerTimeUnit(ctx, 20, TimeUnit.MINUTES)
                val req = HttpRequest.newBuilder()
                    .uri(URI.create(cssUrl))
                    .header("User-Agent", ua)
                    .build()

                val resFut = jdkHttpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                runCatching {
                    val res = resFut.get(5, TimeUnit.SECONDS)
                    if (res.body().contains("吃掉了")) {
                        ctx.result("not found yet")
                        ctx.status(200)
                        return@get
                    } else if (res.headers().allValues("content-type").any { it.equals("text/css", true) }) {
                        // Found
                        ctx.result("found")
                        ctx.status(500)
                        return@get
                    } else {
                        logger.warn("not expected res")
                        ctx.result("unexpected")
                        ctx.status(200)
                        return@get
                    }
                }.onFailure {
                    logger.error("Ex visiting css: ", it)
                    ctx.result("err")
                    ctx.status(200)
                    return@get
                }
            }
        }
    }.start("127.0.0.1", 7000)
    Runtime.getRuntime().addShutdownHook(Thread { app.stop() })
}