plugins {
    id("aap.conventions")
    kotlin("jvm")
    alias(kelvinLibs.plugins.ktor)
    application
}

application {
    mainClass.set("tilgang.AppKt")
}

dependencies {
    implementation(project(":api-kontrakt"))
    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.postmottakKontrakt)
    implementation(kelvinLibs.ktor.server.call.logging)
    implementation(kelvinLibs.ktor.server.call.logging.jvm)
    implementation(kelvinLibs.ktor.server.content.negotiation)
    implementation(kelvinLibs.ktor.server.core)
    implementation(kelvinLibs.ktor.server.netty)
    implementation(kelvinLibs.ktor.server.status.pages)

    implementation(libs.ktorOpenApiGenerator)
    api(libs.server)
    implementation(libs.infrastructure)
    implementation(libs.httpklient)
    implementation(kelvinLibs.micrometer.prometheus)
    implementation(kelvinLibs.ktor.serialization.jackson)
    implementation(kelvinLibs.jackson.datatype.jsr310)
    implementation(kelvinLibs.logback.classic)
    implementation(kelvinLibs.logstash.logback.encoder)
    implementation(kelvinLibs.nimbus.jose.jwt)
    implementation(kelvinLibs.caffeine)
    implementation(libs.lettuce)
    implementation(libs.coroutinesReactor)
    implementation(kelvinLibs.ktor.client.core)
    implementation(kelvinLibs.ktor.client.cio)
    implementation(kelvinLibs.ktor.client.content.negotiation)

    testImplementation(kotlin("test"))
    testImplementation(kelvinLibs.junit.jupiter.params)
    testImplementation(kelvinLibs.assertj.core)
    testImplementation(kelvinLibs.mock.oauth2.server)
    testImplementation(kelvinLibs.ktor.server.test.host)
    testImplementation(libs.testcontainersRedis)
    testImplementation(kelvinLibs.testcontainers)
    testImplementation(libs.coroutinesTest)
    testImplementation(kelvinLibs.ktor.client.mock)
    testImplementation(kelvinLibs.mockk)
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
        implementation("io.netty:netty-resolver-dns:4.2.17.Final") {
            because("4.2.13 er vulnerability CVE-2026-45674")
        }
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}

kotlin.sourceSets["main"].kotlin.srcDirs("main/kotlin")
kotlin.sourceSets["test"].kotlin.srcDirs("test/kotlin")
sourceSets["main"].resources.srcDirs("main/resources")
sourceSets["test"].resources.srcDirs("test/resources")
