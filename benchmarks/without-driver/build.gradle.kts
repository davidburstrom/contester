plugins {
    `java-library`
    id("me.champeau.jmh") version "0.6.6"
}

val jmhVersion: String by project

dependencies {
    implementation(project(":breakpoint"))
    jmh("org.openjdk.jmh:jmh-core:$jmhVersion")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
    jmh("org.openjdk.jmh:jmh-generator-bytecode:$jmhVersion")
}
