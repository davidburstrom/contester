plugins {
    `java-library`
    `maven-publish`
    signing
}

dependencies {
    compileOnly(project(":driver"))
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "contester-breakpoint"
            from(
                components["java"]
            )
            pom {
                name.set("ConTester Breakpoint")
                description.set("Concurrency Tester for JVM languages. This artifact is used to defined breakpoints in production code.")
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
