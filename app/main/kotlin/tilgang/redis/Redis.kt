package tilgang.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.RedisClient
import redis.clients.jedis.params.SetParams
import tilgang.RedisConfig
import java.net.URI
import no.nav.aap.komponenter.miljo.Miljø

data class Key(
    val prefix: String = "",
    val value: String
) {
    fun get(): ByteArray = "$prefix:$value".toByteArray()
}

class Redis private constructor(
    private val jedis: RedisClient,
) : AutoCloseable {
    constructor(config: RedisConfig) : this(
        RedisClient.builder()
            .hostAndPort(HostAndPort(config.uri.host, config.uri.port))
            .clientConfig(
                DefaultJedisClientConfig.builder()
                    .apply {
                        if (!Miljø.erLokal()) {
                            ssl(true)
                            user(config.username)
                        }
                    }
                    .password(config.password).build()
            )
            .build()
    )

    constructor(uri: URI) : this(RedisClient.create(uri))

    fun set(key: Key, value: ByteArray, expireSec: Long = 3600) {
        jedis.set(key.get(), value, SetParams().ex(expireSec))
    }

    operator fun get(key: Key): ByteArray? {
        return jedis.get(key.get())
    }

    fun ready(): Boolean {
        return jedis.ping() == "PONG"
    }

    override fun close() {
        jedis.close()
    }

    companion object {
        val mapper = jacksonObjectMapper()

        inline fun <reified T> ByteArray.deserialize(): T {
            val tr = object : TypeReference<T>() {}
            return mapper.readValue(this, tr)
        }

        inline fun <reified T> T.serialize(): ByteArray {
            return mapper.writeValueAsBytes(this)
        }
    }
}