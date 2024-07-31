package tilgang

import org.junit.jupiter.api.Test
import tilgang.redis.Key
import org.junit.jupiter.api.Assertions.*

class RedisTest {
    @Test
    fun `kan hente cache og cache fjernes etter expire`() {
        Fakes().use {
            val redis = it.redis
            redis.set(Key("helloPello"), "world".toByteArray(), 1)
            assertTrue(redis.get(Key("helloPello"))?.contentEquals("world".toByteArray()) ?: false)
            Thread.sleep(2000)
            assertFalse(redis.exists(Key("helloPello")))
        }
    }
}