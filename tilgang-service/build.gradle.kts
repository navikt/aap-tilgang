plugins {
    id("aap.conventions")
    `java-library`
}

dependencies {
    implementation(libs.coroutinesCore)

    testImplementation(kotlin("test"))
    testImplementation(kelvinLibs.junit.jupiter.params)
    testImplementation(kelvinLibs.assertj.core)
    testImplementation(kelvinLibs.mockk)
    testImplementation(libs.coroutinesTest)
}
