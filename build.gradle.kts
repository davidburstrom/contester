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

        group = "io.github.davidburstrom.contester"
        version = "0.1.0-alpha02"

        plugins.whenPluginAdded {
            if (this is JavaPlugin) {
                /*
                 * Automatically format all Java sources prior to usage.
                 */
                project.tasks.withType<SourceTask>().configureEach {
                    if (this.name != "spotlessJavaApply") {
                        dependsOn("spotlessJavaApply")
                    }
                }

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
                    targetClasses.set(setOf("io.github.davidburstrom.contester.*"))
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
            if (this is MavenPublishPlugin) {
                configure<PublishingExtension> {
                    repositories {
                        maven {
                            name = "MavenCentral"
                            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                            credentials {
                                username = project.properties["ossrh.username"] as String
                                password = project.properties["ossrh.password"] as String
                            }
                        }
                    }
                }
                afterEvaluate {
                    configure<PublishingExtension> {
                        publications.named<MavenPublication>("maven") {
                            pom {
                                url.set("https://github.com/davidburstrom/contester")
                                licenses {
                                    license {
                                        name.set("The Apache License, Version 2.0")
                                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                developers {
                                    developer {
                                        id.set("davidburstrom")
                                        name.set("David Burström")
                                        email.set("david.burstrom@gmail.com")
                                    }
                                }
                                scm {
                                    connection.set("scm:git:git://github.com/davidburstrom/contester.git")
                                    developerConnection.set("scm:git:ssh://github.com/davidburstrom/contester.git")
                                    url.set("https://github.com/davidburstrom/contester")
                                }
                            }
                        }
                    }
                    tasks.named("publishMavenPublicationToMavenCentralRepository") {
                        doFirst {
                            if (gradle.startParameter.isParallelProjectExecutionEnabled) {
                                throw AssertionError("Must execute serially, otherwise Nexus can create multiple staging repos")
                            }
                        }
                    }
                }
            }
        }
    }
}
