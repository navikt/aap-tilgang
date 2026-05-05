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

    @Test
    fun `get på ikke-eksisterende nøkkel returnerer null`() {
        assertNull(redis[Key("finnesIkke", "xyz")])
    }

    @Test
    fun `overskriver eksisterende verdi med ny verdi`() {
        val original = RelevanteIdenter(listOf("11111111111"), emptyList())
        val updated = RelevanteIdenter(listOf("22222222222"), listOf("33333333333"))
        redis.set(Key("overwrite", "test"), original.serialize())
        redis.set(Key("overwrite", "test"), updated.serialize())
        val actual: RelevanteIdenter = redis[Key("overwrite", "test")]!!.deserialize()
        assertEquals(updated, actual)
    }

    @Test
    fun `ulike nøkler lagres uavhengig av hverandre`() {
        val ident1 = RelevanteIdenter(listOf("11111111111"), emptyList())
        val ident2 = RelevanteIdenter(listOf("22222222222"), emptyList())
        redis.set(Key("prefix", "key1"), ident1.serialize())
        redis.set(Key("prefix", "key2"), ident2.serialize())
        assertEquals(ident1, redis[Key("prefix", "key1")]!!.deserialize<RelevanteIdenter>())
        assertEquals(ident2, redis[Key("prefix", "key2")]!!.deserialize<RelevanteIdenter>())
    }

    @Test
    fun `ready returnerer true når Redis kjører`() {
        assertEquals(true, redis.ready())
    }
}