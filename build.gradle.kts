import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask

plugins {
    id("com.diffplug.spotless") version "6.3.0"
    id("info.solidsoft.pitest") version "1.7.4" apply false
}

allprojects {
    beforeEvaluate {
        repositories {
            mavenCentral()
        }

        apply(plugin = "com.diffplug.spotless")
        spotless {
            kotlinGradle {
                target("*.gradle.kts")
                ktlint("0.45.1")
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        group = "com.github.davidburstrom.contester"
        version = "0.1.0-alpha01"

        plugins.whenPluginAdded {
            if (this is JavaPlugin) {
                configure<JavaPluginExtension> {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }

                spotless {
                    java {
                        googleJavaFormat()
                    }
                }

                apply(plugin = "pmd")
                configure<PmdExtension> {
                    ruleSets = listOf()
                    ruleSetConfig = resources.text.fromFile(rootProject.file("config/pmd/rulesets.xml"))
                }

                apply(plugin = "info.solidsoft.pitest")
                configure<PitestPluginExtension> {
                    pitestVersion.set("1.7.5")
                    junit5PluginVersion.set("0.15")
                    timestampedReports.set(false)
                    targetClasses.set(setOf("com.github.davidburstrom.contester.*"))
                    threads.set(4)
                    failWhenNoMutations.set(false)
                    mutators.set(listOf("DEFAULTS"))
                    timeoutConstInMillis.set(200)

                    /* Run Pitest always, if it has a threshold set. */
                    if (ext.has("mutationThreshold")) {
                        mutationThreshold.set(ext.get("mutationThreshold") as Int)

                        tasks.named("build").configure {
                            dependsOn("pitest")
                        }
                    }
                }
                tasks.named<PitestTask>("pitest").configure {
                    inputs.property("src", file("src/test"))
                    onlyIf {
                        (inputs.properties["src"] as File).exists()
                    }

                    /*
                     * Carry over all system properties defined for test tasks into the Pitest tasks, except for the "junit"
                     * ones, as they can interfere with test stability.
                     */
                    systemProperties(tasks.getByName<Test>("test").systemProperties.filterKeys {
                        !it.contains(
                            "junit"
                        )
                    })

                    /*
                     * Include a "pitest" system property to be able to run tests differently if necessary. Use sparingly!
                     */
                    systemProperty("pitest", "true")

                    outputs.cacheIf { true }
                }
            }
        }
    }
}
