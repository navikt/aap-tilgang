package tilgang

import no.nav.aap.tilgang.RelevanteIdenter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.redis.Key
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

@WithFakes
class RedisTest {
    private val redis = Fakes.redis

    @Test
    fun `kan hente cache og cache fjernes etter expire`() {
        val expected = RelevanteIdenter(listOf("31439209766"), emptyList())
        redis.set(Key("helloPello", "1234"), expected.serialize(), 1)
        val actual: RelevanteIdenter = redis[Key("helloPello", "1234")]!!.deserialize()
        assertEquals(expected, actual)
        Thread.sleep(2000)
        assertFalse(redis.exists(Key("helloPello", "1234")))
    }
}