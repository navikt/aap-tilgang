package tilgang.redis

import no.nav.aap.tilgang.RelevanteIdenter
import org.junit.Assert.assertFalse
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tilgang.TestRedis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

@WithRedis
class RedisTest {
    private val redis = TestRedis.server

    @Test
    fun `kan hente cache og cache fjernes etter expire`() {
        val expected = RelevanteIdenter(listOf("31439209766"), emptyList())
        redis.set(Key("helloPello", "1234"), expected.serialize(), 1)
        val actual: RelevanteIdenter = redis[Key("helloPello", "1234")]!!.deserialize()
        Assertions.assertEquals(expected, actual)
        Thread.sleep(2000)
        assertFalse(redis.exists(Key("helloPello", "1234")))
    }
}