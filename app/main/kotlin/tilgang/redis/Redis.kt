package tilgang.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisCoroutinesCommandsImpl
import io.lettuce.core.codec.ByteArrayCodec
import no.nav.aap.komponenter.miljo.Miljø
import tilgang.RedisConfig
import java.net.URI

data class Key(
    val prefix: String = "",
    val value: String
) {
    fun get(): ByteArray = "$prefix:$value".toByteArray()
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class Redis private constructor(
    private val client: RedisClient,
    private val connection: StatefulRedisConnection<ByteArray, ByteArray>
) : AutoCloseable {

    private val commands: RedisCoroutinesCommands<ByteArray, ByteArray> =
        RedisCoroutinesCommandsImpl(connection.reactive())

    constructor(config: RedisConfig) : this(
        buildClient(
            RedisURI.builder()
                .withHost(config.uri.host)
                .withPort(config.uri.port)
                .apply {
                    if (!Miljø.erLokal()) {
                        withSsl(true)
                        withAuthentication(config.username, config.password.toCharArray())
                    }
                }
                .build()
        )
    )

    constructor(uri: URI) : this(buildClient(RedisURI.create(uri.toString())))

    private constructor(client: RedisClient) : this(client, client.connect(ByteArrayCodec.INSTANCE))

    suspend fun set(key: Key, value: ByteArray, expireSec: Long = 3600) {
        commands.set(key.get(), value, SetArgs().ex(expireSec))
    }

    suspend fun get(key: Key): ByteArray? {
        return commands.get(key.get())
    }

    suspend fun ready(): Boolean {
        return commands.ping() == "PONG"
    }

    override fun close() {
        connection.close()
        client.shutdown()
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

        private fun buildClient(uri: RedisURI): RedisClient = RedisClient.create(uri)
    }
}
