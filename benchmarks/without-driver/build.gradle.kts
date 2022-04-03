plugins {
    `java-library`
    id("me.champeau.jmh") version "0.6.6"
}

dependencies {
    implementation(project(":breakpoint"))
}
