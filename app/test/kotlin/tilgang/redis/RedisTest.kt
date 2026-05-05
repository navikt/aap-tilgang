package tilgang.redis

import no.nav.aap.tilgang.RelevanteIdenter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNull
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

@WithFakes
class RedisTest {
    private val redis = Fakes.getRedisServer()

    @Test
    fun `kan hente cache og cache fjernes etter expire`() {
        val expected = RelevanteIdenter(listOf("31439209766"), emptyList())
        redis.set(Key("helloPello", "1234"), expected.serialize(), 1)
        val actual: RelevanteIdenter = redis[Key("helloPello", "1234")]!!.deserialize()
        assertEquals(expected, actual)
        Thread.sleep(2000)
        assertNull(redis[Key("helloPello", "1234")])
    }
}