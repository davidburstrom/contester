pluginManagement {
    plugins {
        id("me.champeau.jmh").version("0.7.1")
    }
}

rootProject.name = "ConTester"

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

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
}

include(":benchmarks:with-driver")
include(":benchmarks:without-driver")
include(":breakpoint")
include(":driver")
include(":examples")
