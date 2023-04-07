rootProject.name = "ConTester"

if (!JavaVersion.current().isJava11Compatible) {
    throw GradleException(
        "The project requires JDK 11 or later to build. Current JDK is ${JavaVersion.current()}."
    )
}

include(":benchmarks:with-driver")
include(":benchmarks:without-driver")
include(":breakpoint")
include(":driver")
include(":examples")
