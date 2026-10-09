plugins {
    `java-library`
    `maven-publish`
}

group = "systems.grebe"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Container4J: OCI-Container (Docker, Podman, …) aus Java steuern – über die Docker-kompatible Kommandozeile der
// jeweiligen Laufzeit. Keine Abhängigkeiten zur Laufzeit; weitere Laufzeiten implementieren ContainerRuntime bzw.
// erweitern CliContainerRuntime.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Bytecode für Java 21 – die Bibliothek läuft damit auch in Anwendungen, die (noch) nicht auf 25 sind
    options.release = 21
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all"))
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("container4j") {
            artifactId = "container4j"
            from(components["java"])
            pom {
                name = "Container4J"
                description = "OCI-Container (Docker, Podman, …) aus Java steuern"
            }
        }
    }
    // Optional ein eigenes Maven-Repository: -Pcontainer4jRepository=<url> -Pcontainer4jRepositoryUser=… -Pcontainer4jRepositoryPassword=…
    providers.gradleProperty("container4jRepository").orNull?.let { url ->
        repositories {
            maven {
                name = "container4j"
                setUrl(url)
                providers.gradleProperty("container4jRepositoryUser").orNull?.let { user ->
                    credentials {
                        username = user
                        password = providers.gradleProperty("container4jRepositoryPassword").orNull
                    }
                }
            }
        }
    }
}
