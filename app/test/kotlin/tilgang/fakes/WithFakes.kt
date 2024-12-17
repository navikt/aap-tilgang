package tilgang.fakes

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

interface WithFakes {
    companion object {
        lateinit var fakes: Fakes

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fakes = Fakes()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            fakes.close()
        }
    }
}