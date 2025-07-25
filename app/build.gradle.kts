plugins {
    id("aap-tilgang.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "3.2.2"
    application
}

val ktorVersion = "3.2.2"
val behandlingsflytVersjon = "0.0.381"
val postmottakVersjon = "0.0.111"
val komponenterVersjon = "1.0.295"
val mockOAuth2ServerVersion = "2.2.1"

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

    implementation("no.nav:ktor-openapi-generator:1.0.117")
    api("no.nav.aap.kelvin:server:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    implementation("redis.clients:jedis:6.0.0")

    testImplementation(kotlin("test"))
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.3")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("no.nav.security:mock-oauth2-server:${mockOAuth2ServerVersion}")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
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
