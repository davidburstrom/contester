plugins {
    `java-library`
}

dependencies {
    implementation(project(":breakpoint"))
    testImplementation(project(":driver"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
}
