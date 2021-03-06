plugins {
    `java-library`
    `maven-publish`
    signing
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
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
                name.set("ConTester Driver")
                description.set("Concurrency Tester for JVM languages. This artifact is used to drive threads from a test suite.")
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
