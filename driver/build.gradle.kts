plugins {
    `java-library`
    `maven-publish`
    signing
}

ext.set("mutationThreshold", 80)

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "contester-driver"
            from(
                components["java"]
            )
            pom {
                name = "ConTester Driver"
                description = "Concurrency Tester for JVM languages. This artifact is used to drive threads from a test suite."
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
