plugins {
    id("aap-tilgang.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "3.0.1"
    application
}

val ktorVersion = "3.0.1"
val aapLibVersion = "3.7.145"
val behandlingsflytVersjon = "0.0.44"
val postmottakVersjon = "0.0.18"

application {
    mainClass.set("tilgang.AppKt")
}

dependencies {
    implementation(project(":api-kontrakt"))
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytVersjon")
    implementation("no.nav.aap.postmottak:kontrakt:$postmottakVersjon")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    implementation("no.nav:ktor-openapi-generator:1.0.46")
    implementation("no.nav.aap.kelvin:server:1.0.55")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.1")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("com.nimbusds:nimbus-jose-jwt:9.46")
    implementation("redis.clients:jedis:5.2.0")

    testImplementation(kotlin("test"))
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.46")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    constraints {
        implementation("org.apache.commons:commons-compress:1.27.1") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
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
