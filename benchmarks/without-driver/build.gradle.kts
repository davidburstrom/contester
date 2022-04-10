plugins {
    `java-library`
    id("me.champeau.jmh") version "0.6.6"
}

dependencies {
    implementation(project(":breakpoint"))
    jmh("org.openjdk.jmh:jmh-core:1.35")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.35")
    jmh("org.openjdk.jmh:jmh-generator-bytecode:1.35")
}
