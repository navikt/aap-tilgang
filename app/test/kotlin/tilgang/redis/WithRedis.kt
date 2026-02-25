package tilgang.redis

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import tilgang.TestRedis

class RedisExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        TestRedis.start()
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(RedisExtension::class)
annotation class WithRedis
