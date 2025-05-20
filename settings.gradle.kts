pluginManagement {
    plugins {
        id("me.champeau.jmh").version("0.7.3")
    }
}

rootProject.name = "contester"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":benchmarks:with-driver")
include(":benchmarks:without-driver")
include(":breakpoint")
include(":driver")
include(":examples")
