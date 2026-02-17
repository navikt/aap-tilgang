plugins {
    id("aap.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "3.4.0"
    application
}

application {
    mainClass.set("tilgang.AppKt")
}

dependencies {
    implementation(project(":api-kontrakt"))
    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.postmottakKontrakt)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerCallLoggingJvm)
    implementation(libs.ktorServerContentNegotation)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerStatusPages)

    implementation(libs.ktorOpenApiGenerator)
    api(libs.server)
    implementation(libs.infrastructure)
    implementation(libs.httpklient)
    implementation(libs.micrometerRegistryPrometheus)
    implementation(libs.ktorSerializationJackson)
    implementation(libs.jacksonDatatypeJsr310)
    implementation(libs.logbackClassic)
    implementation(libs.logstashLogbackEncoder)
    implementation(libs.joseJwt)
    implementation(libs.jedis)

    testImplementation(kotlin("test"))
    testImplementation(libs.joseJwt)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.assertJ)
    testImplementation(libs.mockOauth2Server)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.testcontainersRedis)
    testImplementation(libs.testcontainers)
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
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
