plugins {
    id("aap.conventions")
    `java-library`
}

dependencies {
    api(project(":api-kontrakt"))
    implementation(libs.coroutinesCore)

    testImplementation(kotlin("test"))
    testImplementation(kelvinLibs.junit.jupiter.params)
    testImplementation(kelvinLibs.assertj.core)
    testImplementation(kelvinLibs.mockk)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.behandlingsflytKontrakt)
}
