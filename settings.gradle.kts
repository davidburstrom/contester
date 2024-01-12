pluginManagement {
    plugins {
        id("me.champeau.jmh").version("0.7.2")
    }
}

rootProject.name = "contester"

if (!JavaVersion.current().isJava11Compatible) {
    throw GradleException(
        "The project requires JDK 11 or later to build. Current JDK is ${JavaVersion.current()}."
    )
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":benchmarks:with-driver")
include(":benchmarks:without-driver")
include(":breakpoint")
include(":driver")
include(":examples")
