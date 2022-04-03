plugins {
    `java-library`
    `maven-publish`
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
        }
    }
}
