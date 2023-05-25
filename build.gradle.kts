import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask

plugins {
    id("com.diffplug.spotless") version "6.19.0"
    id("info.solidsoft.pitest") version "1.9.11" apply false
    id("com.github.ben-manes.versions") version "0.46.0"
}

val pitestMainVersion = "1.14.1"
val pitestJUnit5PluginVersion = "1.2.0"
ext["jmhVersion"] = "1.36"

configurations {
    register("dependencyUpdates")
}

dependencies {
    "dependencyUpdates"("org.pitest:pitest-junit5-plugin:$pitestJUnit5PluginVersion")
    "dependencyUpdates"("org.pitest:pitest:$pitestMainVersion")
}

allprojects {
    beforeEvaluate {
        apply(plugin = "com.diffplug.spotless")
        spotless {
            kotlinGradle {
                target("*.gradle.kts")
                ktlint("0.47.1")
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        group = "io.github.davidburstrom.contester"
        version = "0.2.0"

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

                project.tasks.withType<JavaCompile> {
                    options.release.set(8)
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

            }
            if (this is MavenPublishPlugin && project.properties["ossrh.username"] != null) {
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

        afterEvaluate {
            if (plugins.hasPlugin(JavaPlugin::class.java)) {
                apply(plugin = "info.solidsoft.pitest")
                configure<PitestPluginExtension> {
                    pitestVersion.set(pitestMainVersion)
                    junit5PluginVersion.set(pitestJUnit5PluginVersion)
                    timestampedReports.set(false)
                    targetClasses.set(setOf("io.github.davidburstrom.contester.*"))
                    threads.set(4)
                    failWhenNoMutations.set(false)
                    mutators.set(listOf("DEFAULTS", "EXTENDED"))
                    timeoutConstInMillis.set(200)

                    /* Run Pitest always, if it has a threshold set. */
                    if (ext.has("mutationThreshold")) {
                        mutationThreshold.set(ext.get("mutationThreshold") as Int)

                        tasks.named("build").configure {
                            dependsOn("pitest")
                        }
                    }
                }
                dependencies {
                    "pitest"("com.groupcdg.arcmutate:base:1.0.4")
                    "pitest"("com.groupcdg.pitest:pitest-accelerator-junit5:1.0.5")
                }
                tasks.named<PitestTask>("pitest").configure {
                    inputs.property("src", file("src/test"))
                    inputs.file(rootProject.file("cdg-pitest-licence.txt"))
                    onlyIf {
                        (inputs.properties["src"] as File).exists()
                    }

                    javaLauncher.set(
                        project.extensions.getByType<JavaToolchainService>().launcherFor {
                            languageVersion.set(JavaLanguageVersion.of(8))
                        }
                    )

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
