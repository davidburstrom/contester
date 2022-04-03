plugins {
    `java-library`
    `maven-publish`
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
        }
    }
}
