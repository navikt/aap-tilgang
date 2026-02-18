package tilgang

import com.redis.testcontainers.RedisContainer
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import tilgang.redis.Redis

class TestRedis : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val redisContainer by lazy { RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG)) }

    val uri: URI
        get() = URI(redisContainer.redisURI)

    val server: Redis by lazy { Redis(uri) }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        redisContainer.start()

        val timeout = System.currentTimeMillis() + 10.seconds.inWholeMilliseconds
        while (System.currentTimeMillis() < timeout) {
            try {
                server.ready()
                break
            } catch (_: Throwable) {
                Thread.sleep(500.milliseconds.inWholeMilliseconds)
            }
        }
    }

    fun stop() {
        if (!started.get()) return
        server.close()
        redisContainer.stop()
    }

    override fun close() {
        stop()
    }
}
