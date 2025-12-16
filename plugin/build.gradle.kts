val ktorVersion = "3.3.3"
val komponenterVersjon = "1.0.464"
val junitVersion = "6.0.1"

plugins {
    id("aap-tilgang.conventions")
    `maven-publish`
    `java-library`
}

group = "no.nav.aap.tilgang"

apply(plugin = "maven-publish")
apply(plugin = "java-library")

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

dependencies {
    api(project(":api-kontrakt"))
    api(project(":plugin-kontrakt"))

    compileOnly(libs.httpklient)
    compileOnly(libs.json)
    compileOnly(libs.infrastructure)

    implementation(libs.ktorServerAuthJwt)
    implementation(libs.logbackClassic)
    implementation(libs.ktorOpenApiGenerator)
    implementation(libs.ktorServerCore)

    implementation(libs.joseJwt)

    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerAuthJwt)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerCallId)
    implementation(libs.ktorServerContentNegotation)
    implementation(libs.ktorServerMetricsMicrometer)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerCors)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorSerializationJackson)
    implementation(libs.ktorServerDoubleReceive)

    testImplementation(libs.server)
    testImplementation(libs.httpklient)
    testImplementation(libs.json)
    testImplementation(libs.infrastructure)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testImplementation(libs.assertJ)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
