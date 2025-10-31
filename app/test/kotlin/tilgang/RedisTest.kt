package tilgang

import no.nav.aap.tilgang.RelevanteIdenter
import org.junit.jupiter.api.Test
import tilgang.redis.Key
import org.junit.jupiter.api.Assertions.*
import tilgang.fakes.Fakes
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

class RedisTest {
    @Test
    fun `kan hente cache og cache fjernes etter expire`() {
        Fakes().use {
            val redis = it.redis
            val expected = RelevanteIdenter(listOf("31439209766"), emptyList())
            redis.set(Key("helloPello", "1234"), expected.serialize(), 1)
            val actual: RelevanteIdenter = redis[Key("helloPello", "1234")]!!.deserialize()
            assertEquals(expected, actual)
            Thread.sleep(2000)
            assertFalse(redis.exists(Key("helloPello", "1234")))
        }
    }
}