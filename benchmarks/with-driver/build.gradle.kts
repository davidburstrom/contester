plugins {
    `java-library`
    id("me.champeau.jmh")
}

val jmhVersion: String by project

dependencies {
    implementation(project(":breakpoint"))
    implementation(project(":driver"))
    jmh("org.openjdk.jmh:jmh-core:$jmhVersion")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
    jmh("org.openjdk.jmh:jmh-generator-bytecode:$jmhVersion")
}
