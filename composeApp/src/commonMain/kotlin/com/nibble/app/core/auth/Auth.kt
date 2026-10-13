package com.nibble.app.core.auth

import com.nibble.app.core.domain.Time
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Accounts exist for the web app and the coming sync layer — the reader itself never
 * requires one (local-first stays the default; signing in is additive).
 *
 * The backend is Firebase Auth spoken over its REST surface (identitytoolkit +
 * securetoken) rather than a platform SDK, so the SAME code serves Android, iOS and the
 * future web target. Firebase Web API keys identify the project, they are not secrets.
 */
object AuthConfig {
    /** Firebase Web API key (console → Project settings → General). Blank = accounts off. */
    const val FIREBASE_API_KEY: String = ""

    /**
     * Origin of a local Firebase AUTH EMULATOR (e.g. "http://10.0.2.2:9099") for
     * development. null = production Firebase. Never ship a build with this set.
     */
    val EMULATOR_ORIGIN: String? = null
}

/** A signed-in identity. Tokens live in the same private JSON as everything else. */
@Serializable
data class Account(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

interface AuthService {
    /** False when this build has no key configured — the UI says so plainly. */
    val isConfigured: Boolean
    suspend fun signUp(email: String, password: String): Result<Account>
    suspend fun signIn(email: String, password: String): Result<Account>
    suspend fun refresh(account: Account): Result<Account>
    suspend fun sendPasswordReset(email: String): Result<Unit>
}

/** Auth failure with a message already in PLAIN tone — safe to show verbatim (§11). */
class AuthException(message: String) : Exception(message)

class FirebaseAuth(
    private val apiKey: String = AuthConfig.FIREBASE_API_KEY,
    emulatorOrigin: String? = AuthConfig.EMULATOR_ORIGIN,
    /** Injectable for tests; production uses the platform [httpPost]. */
    private val post: suspend (url: String, body: String, contentType: String) -> HttpTextResponse =
        { url, body, type -> httpPost(url, body, type) },
) : AuthService {

    private val identityBase = emulatorOrigin?.trimEnd('/')?.plus("/identitytoolkit.googleapis.com")
        ?: "https://identitytoolkit.googleapis.com"
    private val tokenBase = emulatorOrigin?.trimEnd('/')?.plus("/securetoken.googleapis.com")
        ?: "https://securetoken.googleapis.com"
    // The emulator accepts any key, so a blank key is only disqualifying in production.
    override val isConfigured = apiKey.isNotBlank() || emulatorOrigin != null

    // encodeDefaults keeps returnSecureToken=true in the payload — Firebase requires it.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key get() = apiKey.ifBlank { "emulator" }

    override suspend fun signUp(email: String, password: String) =
        credentialCall("$identityBase/v1/accounts:signUp?key=$key", email, password)

    override suspend fun signIn(email: String, password: String) =
        credentialCall("$identityBase/v1/accounts:signInWithPassword?key=$key", email, password)

    private suspend fun credentialCall(url: String, email: String, password: String): Result<Account> = runCatching {
        val body = json.encodeToString(
            CredentialRequest.serializer(),
            CredentialRequest(email.trim(), password),
        )
        val resp = wrapped { post(url, body, "application/json") }
        if (resp.status !in 200..299) throw AuthException(friendlyError(resp.body))
        val r = json.decodeFromString(CredentialResponse.serializer(), resp.body)
        Account(
            uid = r.localId,
            email = r.email ?: email.trim(),
            idToken = r.idToken,
            refreshToken = r.refreshToken,
            expiresAtMillis = Time.nowMillis() + (r.expiresIn.toLongOrNull() ?: 3600L) * 1000,
        )
    }

    override suspend fun refresh(account: Account): Result<Account> = runCatching {
        val body = "grant_type=refresh_token&refresh_token=${account.refreshToken}"
        val resp = wrapped { post("$tokenBase/v1/token?key=$key", body, "application/x-www-form-urlencoded") }
        if (resp.status !in 200..299) throw AuthException(friendlyError(resp.body))
        val r = json.decodeFromString(RefreshResponse.serializer(), resp.body)
        account.copy(
            idToken = r.id_token,
            refreshToken = r.refresh_token,
            expiresAtMillis = Time.nowMillis() + (r.expires_in.toLongOrNull() ?: 3600L) * 1000,
        )
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val body = """{"requestType":"PASSWORD_RESET","email":"${email.trim()}"}"""
        val resp = wrapped { post("$identityBase/v1/accounts:sendOobCode?key=$key", body, "application/json") }
        if (resp.status !in 200..299) throw AuthException(friendlyError(resp.body))
    }

    private suspend fun wrapped(block: suspend () -> HttpTextResponse): HttpTextResponse =
        try {
            block()
        } catch (e: HttpUnreachable) {
            throw AuthException(OFFLINE)
        }

    @Serializable
    private data class CredentialRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)

    @Serializable
    private data class CredentialResponse(
        val localId: String,
        val email: String? = null,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String = "3600",
    )

    @Serializable
    private data class RefreshResponse(
        val id_token: String,
        val refresh_token: String,
        val expires_in: String = "3600",
        val user_id: String = "",
    )

    companion object {
        const val OFFLINE = "Couldn't reach the server. Check your connection and try again."

        /** Map Firebase error codes to PLAIN sentences — errors never joke (§11). */
        fun friendlyError(body: String): String {
            val code = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            }.getOrNull() ?: return OFFLINE
            return when {
                code.startsWith("EMAIL_EXISTS") -> "That email already has an account. Sign in instead."
                code.startsWith("EMAIL_NOT_FOUND") ||
                    code.startsWith("INVALID_PASSWORD") ||
                    code.startsWith("INVALID_LOGIN_CREDENTIALS") -> "Email or password didn't match."
                code.startsWith("INVALID_EMAIL") || code.startsWith("MISSING_EMAIL") -> "That doesn't look like an email address."
                code.startsWith("WEAK_PASSWORD") || code.startsWith("MISSING_PASSWORD") -> "Passwords need at least 6 characters."
                code.startsWith("TOO_MANY_ATTEMPTS") -> "Too many attempts. Wait a few minutes and try again."
                code.startsWith("USER_DISABLED") -> "This account has been disabled."
                code.startsWith("TOKEN_EXPIRED") || code.startsWith("INVALID_REFRESH_TOKEN") -> "Your session expired. Sign in again."
                else -> "Something went wrong signing you in. Try again."
            }
        }
    }
}

/** Used when no backend is configured: every call explains itself instead of failing oddly. */
class NoopAuthService : AuthService {
    override val isConfigured = false
    private val off = AuthException("Accounts aren't set up in this build.")
    override suspend fun signUp(email: String, password: String) = Result.failure<Account>(off)
    override suspend fun signIn(email: String, password: String) = Result.failure<Account>(off)
    override suspend fun refresh(account: Account) = Result.failure<Account>(off)
    override suspend fun sendPasswordReset(email: String) = Result.failure<Unit>(off)
}
