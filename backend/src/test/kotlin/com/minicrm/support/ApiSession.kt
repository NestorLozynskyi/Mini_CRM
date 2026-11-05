package com.minicrm.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class Reply(val status: Int, val body: JsonNode?, val raw: String, val headers: java.net.http.HttpHeaders) {
    val code: String? get() = body?.get("code")?.asString()
    fun fields(): Set<String> = body?.get("errors")?.let { e -> (0 until e.size()).map { e[it]["field"].asString() }.toSet() } ?: emptySet()
}

/**
 * Окремий «браузер»: власне сховище cookie (сесія + XSRF-TOKEN), реальні HTTP-запити на запущений бекенд.
 * Кожен користувач у тестах ізоляції — окремий екземпляр.
 */
class ApiSession(private val baseUrl: String, val ip: String = "10.0.0.1", var timeZone: String = "UTC") {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val http = HttpClient.newBuilder().cookieHandler(cookies).build()

    fun cookie(name: String): String? = cookies.cookieStore.cookies.firstOrNull { it.name == name }?.value

    private fun csrf(): String {
        cookie("XSRF-TOKEN")?.let { return it }
        send("GET", "/api/auth/csrf", null)
        return cookie("XSRF-TOKEN") ?: error("XSRF-TOKEN cookie was not issued")
    }

    fun get(path: String) = send("GET", path, null)
    fun post(path: String, json: String? = null, withCsrf: Boolean = true) = send("POST", path, json ?: "", withCsrf)
    fun put(path: String, json: String) = send("PUT", path, json)
    fun patch(path: String, json: String) = send("PATCH", path, json)
    fun delete(path: String) = send("DELETE", path, null, true)

    fun send(method: String, path: String, json: String?, withCsrf: Boolean = method != "GET"): Reply {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "application/json")
            .header("X-Forwarded-For", ip)
            .header("X-Time-Zone", timeZone)
        if (withCsrf) builder.header("X-XSRF-TOKEN", csrf())
        val publisher = if (json == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(json)
        if (json != null) builder.header("Content-Type", "application/json")
        builder.method(method, publisher)
        val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val body = res.body()
        val node = if (res.headers().firstValue("Content-Type").orElse("").contains("json") && body.isNotBlank()) MAPPER.readTree(body) else null
        return Reply(res.statusCode(), node, body, res.headers())
    }

    // ---- зручні сценарії

    fun signUp(email: String, password: String = "correct-horse-1") =
        post("/api/auth/sign-up", """{"email":"$email","password":"$password"}""").also { check(it.status == 201) { "sign-up failed: ${it.raw}" } }

    fun startDemo() = post("/api/auth/demo").also { check(it.status == 201) { "demo failed: ${it.raw}" } }

    fun createClient(name: String, type: String = "company", tags: List<String> = emptyList()): Long {
        val tagJson = tags.joinToString(",") { "\"$it\"" }
        val r = post("/api/clients", """{"type":"$type","name":"$name","tags":[$tagJson]}""")
        check(r.status == 201) { "client failed: ${r.raw}" }
        return r.body!!["id"].asLong()
    }

    fun createDeal(clientId: Long, title: String, amount: Long = 1000, stage: String = "lead"): Long {
        val r = post("/api/deals", """{"title":"$title","clientId":$clientId,"amount":$amount,"stage":"$stage","expectedClose":"2026-12-01"}""")
        check(r.status == 201) { "deal failed: ${r.raw}" }
        return r.body!!["id"].asLong()
    }

    fun createInteraction(link: String, text: String = "Call notes", date: String = "2026-11-03"): Long {
        val r = post("/api/interactions", """{"type":"call","date":"$date","description":"$text",$link}""")
        check(r.status == 201) { "interaction failed: ${r.raw}" }
        return r.body!!["id"].asLong()
    }

    fun createTask(link: String, title: String = "Follow up", due: String = "2026-11-04"): Long {
        val r = post("/api/tasks", """{"title":"$title","due":"$due",$link}""")
        check(r.status == 201) { "task failed: ${r.raw}" }
        return r.body!!["id"].asLong()
    }

    companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().build()
    }
}

fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }
