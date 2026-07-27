package tilgang.auth

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.time.Duration.Companion.seconds
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.http.createHttpClient

interface ITokenProvider {
    suspend fun m2mToken(scope: String): String
    suspend fun oboToken(scope: String, currentToken: OidcToken): String
}

object TokenProvider : ITokenProvider {
    private const val TOKEN_SAFETY_MARGIN_SECONDS = 30L
    private const val FALLBACK_EXPIRES_IN_SECONDS = 60L

    private val texasGateway: TexasGateway = TexasGateway(createHttpClient(2.seconds))
    private val clock: Clock = Clock.systemUTC()
    private val singleFlightScope = CoroutineScope(SupervisorJob())
    private val m2mCache = Caffeine.newBuilder()
        .maximumSize(128)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String, CachedToken>()
    private val oboCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String, CachedToken>()
    private val m2mInFlight = ConcurrentHashMap<String, Deferred<CachedToken>>()
    private val oboInFlight = ConcurrentHashMap<String, Deferred<CachedToken>>()

    override suspend fun oboToken(scope: String, currentToken: OidcToken): String {
        val cacheKey = "$scope:${sha256(currentToken.token())}"
        return hentToken(
            key = cacheKey,
            cache = oboCache,
            inFlight = oboInFlight,
        ) {
            val response = texasGateway.exchangeToken(scope, currentToken)
            CachedToken(
                token = response.access_token,
                expiresAt = expiryFrom(response.expires_in),
            )
        }.token
    }

    override suspend fun m2mToken(scope: String): String {
        return hentToken(
            key = scope,
            cache = m2mCache,
            inFlight = m2mInFlight,
        ) {
            val response = texasGateway.machineToMachineToken(scope)
            CachedToken(
                token = response.access_token,
                expiresAt = expiryFrom(response.expires_in),
            )
        }.token
    }

    private fun expiryFrom(expiresInSeconds: Long?): Instant {
        val ttl = (expiresInSeconds ?: FALLBACK_EXPIRES_IN_SECONDS) - TOKEN_SAFETY_MARGIN_SECONDS
        return Instant.now(clock).plusSeconds(maxOf(1L, ttl))
    }

    private data class CachedToken(
        val token: String,
        val expiresAt: Instant,
    )

    private fun sha256(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun CachedToken.isExpired(clock: Clock): Boolean {
        return !expiresAt.isAfter(Instant.now(clock))
    }

    private suspend fun hentToken(
        key: String,
        cache: com.github.benmanes.caffeine.cache.Cache<String, CachedToken>,
        inFlight: ConcurrentHashMap<String, Deferred<CachedToken>>,
        loader: suspend () -> CachedToken,
    ): CachedToken {
        cache.getIfPresent(key)?.takeIf { !it.isExpired(clock) }?.let { return it }

        val deferred = inFlight.computeIfAbsent(key) {
            singleFlightScope.async(start = CoroutineStart.LAZY) { loader() }
                .also { nyDeferred ->
                    nyDeferred.invokeOnCompletion { inFlight.remove(key, nyDeferred) }
                    nyDeferred.start()
                }
        }

        val token = deferred.await()
        cache.put(key, token)
        return token
    }
}