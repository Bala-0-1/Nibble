package com.nibble.app.core.auth

/**
 * The one HTTP shape auth needs: a POST with a body, returning status + text.
 * Hand-rolled expect/actual (HttpURLConnection / NSURLSession / fetch when the web
 * target lands) instead of an HTTP client dependency — same reasoning as the custom
 * store/navigator: one small need, no framework.
 */
data class HttpTextResponse(val status: Int, val body: String)

/** Thrown for transport-level failures (no route, DNS, timeout) — never for HTTP 4xx/5xx. */
class HttpUnreachable(message: String) : Exception(message)

expect suspend fun httpPost(url: String, body: String, contentType: String): HttpTextResponse
