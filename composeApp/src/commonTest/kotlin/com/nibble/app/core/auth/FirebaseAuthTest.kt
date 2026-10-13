package com.nibble.app.core.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The REST client parses, maps errors to PLAIN sentences, and targets the right URLs. */
class FirebaseAuthTest {

    private class Recorder(val status: Int, val body: String) {
        var lastUrl: String? = null
        var lastBody: String? = null
        var lastType: String? = null
        val post: suspend (String, String, String) -> HttpTextResponse = { url, body, type ->
            lastUrl = url; lastBody = body; lastType = type
            HttpTextResponse(this.status, this.body)
        }
    }

    private val okCredential =
        """{"localId":"u1","email":"a@b.co","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""

    @Test
    fun signInParsesAnAccount() = runTest {
        val rec = Recorder(200, okCredential)
        val account = FirebaseAuth(apiKey = "k", emulatorOrigin = null, post = rec.post)
            .signIn("a@b.co", "hunter22").getOrThrow()
        assertEquals("u1", account.uid)
        assertEquals("a@b.co", account.email)
        assertEquals("tok", account.idToken)
        assertEquals("ref", account.refreshToken)
        assertTrue(rec.lastUrl!!.startsWith("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"))
        assertTrue(rec.lastUrl!!.endsWith("key=k"))
        assertEquals("application/json", rec.lastType)
    }

    @Test
    fun signUpTargetsTheSignUpEndpoint() = runTest {
        val rec = Recorder(200, okCredential)
        FirebaseAuth(apiKey = "k", emulatorOrigin = null, post = rec.post).signUp("a@b.co", "hunter22")
        assertTrue(rec.lastUrl!!.contains("accounts:signUp"))
        assertTrue(rec.lastBody!!.contains("returnSecureToken"))
    }

    @Test
    fun knownErrorsBecomePlainSentences() = runTest {
        val cases = mapOf(
            "EMAIL_EXISTS" to "That email already has an account. Sign in instead.",
            "INVALID_LOGIN_CREDENTIALS" to "Email or password didn't match.",
            "INVALID_EMAIL" to "That doesn't look like an email address.",
            // Firebase suffixes some codes with an explanation — prefix matching handles it.
            "WEAK_PASSWORD : Password should be at least 6 characters" to "Passwords need at least 6 characters.",
            "TOO_MANY_ATTEMPTS_TRY_LATER" to "Too many attempts. Wait a few minutes and try again.",
        )
        for ((code, friendly) in cases) {
            val rec = Recorder(400, """{"error":{"message":"$code","code":400}}""")
            val result = FirebaseAuth(apiKey = "k", emulatorOrigin = null, post = rec.post).signIn("a@b.co", "x")
            assertEquals(friendly, result.exceptionOrNull()?.message, "for code $code")
        }
    }

    @Test
    fun refreshParsesTheSecureTokenShape() = runTest {
        val rec = Recorder(200, """{"id_token":"tok2","refresh_token":"ref2","expires_in":"3600","user_id":"u1"}""")
        val old = Account("u1", "a@b.co", "tok", "ref", 0L)
        val fresh = FirebaseAuth(apiKey = "k", emulatorOrigin = null, post = rec.post).refresh(old).getOrThrow()
        assertEquals("tok2", fresh.idToken)
        assertEquals("ref2", fresh.refreshToken)
        assertTrue(rec.lastUrl!!.startsWith("https://securetoken.googleapis.com/v1/token"))
        assertEquals("application/x-www-form-urlencoded", rec.lastType)
    }

    @Test
    fun transportFailureIsThePlainOfflineMessage() = runTest {
        val svc = FirebaseAuth(apiKey = "k", emulatorOrigin = null, post = { _, _, _ -> throw HttpUnreachable("boom") })
        val result = svc.signIn("a@b.co", "x")
        assertEquals(FirebaseAuth.OFFLINE, result.exceptionOrNull()?.message)
    }

    @Test
    fun emulatorOriginRewritesBothHosts() = runTest {
        val rec = Recorder(200, okCredential)
        val svc = FirebaseAuth(apiKey = "", emulatorOrigin = "http://10.0.2.2:9099", post = rec.post)
        assertTrue(svc.isConfigured)
        svc.signIn("a@b.co", "x")
        assertTrue(rec.lastUrl!!.startsWith("http://10.0.2.2:9099/identitytoolkit.googleapis.com/v1/"))
        val old = Account("u1", "a@b.co", "t", "r", 0L)
        svc.refresh(old)
        assertTrue(rec.lastUrl!!.startsWith("http://10.0.2.2:9099/securetoken.googleapis.com/v1/token"))
    }

    @Test
    fun blankKeyWithoutEmulatorIsUnconfigured() {
        assertTrue(!FirebaseAuth(apiKey = "", emulatorOrigin = null).isConfigured)
    }
}
