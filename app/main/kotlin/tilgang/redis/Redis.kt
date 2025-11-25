package tilgang.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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
    private val pool: JedisPool
) : AutoCloseable {
    constructor(config: RedisConfig) : this(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(config.uri.host, config.uri.port),
            DefaultJedisClientConfig.builder()
                .apply {
                    if (!Miljø.erLokal()) {
                        ssl(true)
                        user(config.username)
                    }
                }
                .password(config.password).build()
        )
    )

    constructor(uri: URI) : this(JedisPool(uri))

    fun set(key: Key, value: ByteArray, expireSec: Long = 3600) {
        pool.resource.use {
            it.set(key.get(), value, SetParams().ex(expireSec))
        }
    }

    operator fun get(key: Key): ByteArray? {
        pool.resource.use {
            return it.get(key.get())
        }
    }

    fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }

    fun exists(key: Key): Boolean {
        pool.resource.use {
            return it.exists(key.get())
        }
    }

    override fun close() {
        pool.close()
    }

    companion object {
        inline fun <reified T> ByteArray.deserialize(): T {
            val mapper = jacksonObjectMapper()
            val tr = object : TypeReference<T>() {}
            return mapper.readValue(this, tr)
        }

        inline fun <reified T> T.serialize(): ByteArray {
            val mapper = jacksonObjectMapper()
            return mapper.writeValueAsBytes(this)
        }
    }
}