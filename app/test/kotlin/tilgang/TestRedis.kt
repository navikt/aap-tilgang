package tilgang

import com.redis.testcontainers.RedisContainer
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import tilgang.redis.Redis

object TestRedis {
    private const val TI_SEKUNDER_MILLIS: Long = 10_000
    private const val HALVT_SEKUND_MILLIS: Long = 500

    private val started = AtomicBoolean(false)
    private val redisContainer by lazy { RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG)) }

    val uri: URI
        get() = URI(redisContainer.redisURI)

    val redis: Redis by lazy { Redis(uri) }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        redisContainer.start()

        val timeout = System.currentTimeMillis() + TI_SEKUNDER_MILLIS
        while (System.currentTimeMillis() < timeout) {
            try {
                redis.ready()
                break
            } catch (_: Throwable) {
                Thread.sleep(HALVT_SEKUND_MILLIS)
            }
        }
    }

    fun stop() {
        if (!started.get()) return
        redis.close()
        redisContainer.stop()
    }
}
