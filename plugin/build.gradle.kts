val ktorVersion = "3.0.2"
val komponenterVersjon = "1.0.98"

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

    compileOnly("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    compileOnly("no.nav.aap.kelvin:json:$komponenterVersjon")
    compileOnly("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")

    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.14")
    implementation("no.nav:ktor-openapi-generator:1.0.50")
    implementation("io.ktor:ktor-server-core:$ktorVersion")

    implementation("com.nimbusds:nimbus-jose-jwt:9.47")

    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")

    testImplementation("no.nav.aap.kelvin:server:$komponenterVersjon")
    testImplementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    testImplementation("no.nav.aap.kelvin:json:$komponenterVersjon")
    testImplementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
}
