import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("aap.conventions")
    `maven-publish`
    `java-library`
}

group = "no.nav.aap.tilgang"

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            version = project.findProperty("version")?.toString() ?: "0.0.0"
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/aap-tilgang")
            credentials {
                username = "x-access-token"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

kotlin {
    explicitApi = ExplicitApiMode.Warning
}

dependencies {
    api(project(":api-kontrakt"))
    api(project(":plugin-kontrakt"))

    compileOnly(libs.server)
    compileOnly(libs.json)
    compileOnly(libs.infrastructure)

    implementation(kelvinLibs.logback.classic)
    implementation(libs.ktorOpenApiGenerator)
    implementation(kelvinLibs.ktor.server.core)

    implementation(kelvinLibs.nimbus.jose.jwt)
    implementation(kelvinLibs.caffeine)

    implementation(kelvinLibs.ktor.server.auth)
    implementation(kelvinLibs.ktor.server.auth.jwt)
    implementation(kelvinLibs.ktor.server.call.logging)
    implementation(kelvinLibs.ktor.server.call.id)
    implementation(kelvinLibs.ktor.server.content.negotiation)
    implementation(kelvinLibs.ktor.server.metrics.micrometer)
    implementation(kelvinLibs.ktor.server.netty)
    implementation(kelvinLibs.ktor.server.status.pages)
    implementation(kelvinLibs.ktor.serialization.jackson)
    implementation("io.ktor:ktor-server-double-receive:${kelvinLibs.versions.ktor.get()}")

    implementation(kelvinLibs.ktor.client.core)
    implementation(kelvinLibs.ktor.client.cio)
    implementation(kelvinLibs.ktor.client.content.negotiation)

    testImplementation(libs.server)
    testImplementation(libs.json)
    testImplementation(libs.infrastructure)
    testImplementation(kelvinLibs.junit.jupiter.api)
    testRuntimeOnly(kelvinLibs.junit.jupiter.engine)
    testImplementation(kelvinLibs.assertj.core)
    testRuntimeOnly(kelvinLibs.junit.platform.launcher)
}
