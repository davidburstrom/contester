import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask

plugins {
    id("com.diffplug.spotless") version "7.0.0.BETA4"
    id("info.solidsoft.pitest") version "1.15.0" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("net.ltgt.errorprone") version "4.1.0" apply false
}

val errorProneVersion = "2.34.0"
val ktlintVersion = "1.4.0"
val pitestMainVersion = "1.17.0"
val pitestJUnit5PluginVersion = "1.2.1"
ext["jmhVersion"] = "1.37"

configurations {
    register("dependencyUpdates")
}

dependencies {
    "dependencyUpdates"("org.pitest:pitest-junit5-plugin:$pitestJUnit5PluginVersion")
    "dependencyUpdates"("org.pitest:pitest:$pitestMainVersion")
    "dependencyUpdates"("com.pinterest.ktlint:ktlint-bom:$ktlintVersion")
}

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(mapOf("ktlint_standard_trailing-comma-on-call-site" to "disabled"))
    }
}

allprojects {
    beforeEvaluate {
        apply(plugin = "com.diffplug.spotless")

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        group = "io.github.davidburstrom.contester"
        version = "0.2.0"

        plugins.whenPluginAdded {
            if (this is JavaPlugin) {
                configure<JavaPluginExtension> {
                    toolchain {
                        // Could theoretically be version 8, but it's not compatible with
                        // ErrorProne. Therefore, the JavaCompile release option is used.
                        languageVersion = JavaLanguageVersion.of(17)
                    }
                }

                /*
                 * Automatically format all Java sources prior to usage.
                 */
                project.tasks.withType<SourceTask>().configureEach {
                    if (this.name != "spotlessJavaApply") {
                        dependsOn("spotlessJavaApply")
                    }
                }

                project.tasks.withType<JavaCompile> {
                    options.release = 8
                    options.compilerArgs.add("-Werror")
                }

                spotless {
                    java {
                        googleJavaFormat()
                        licenseHeaderFile(rootProject.file("config/license-header.txt"))
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
                                url = "https://github.com/davidburstrom/contester"
                                licenses {
                                    license {
                                        name = "The Apache License, Version 2.0"
                                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                                    }
                                }
                                developers {
                                    developer {
                                        id = "davidburstrom"
                                        name = "David Burström"
                                        email = "david.burstrom@gmail.com"
                                    }
                                }
                                scm {
                                    connection = "scm:git:git://github.com/davidburstrom/contester.git"
                                    developerConnection = "scm:git:ssh://github.com/davidburstrom/contester.git"
                                    url = "https://github.com/davidburstrom/contester"
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
                    pitestVersion = pitestMainVersion
                    junit5PluginVersion = pitestJUnit5PluginVersion
                    timestampedReports = false
                    targetClasses = setOf("io.github.davidburstrom.contester.*")
                    threads = 4
                    failWhenNoMutations = false
                    mutators = listOf("DEFAULTS")
                    timeoutConstInMillis = 200

                    /* Run Pitest always, if it has a threshold set. */
                    if (ext.has("mutationThreshold")) {
                        mutationThreshold = ext.get("mutationThreshold") as Int

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

                    javaLauncher = project.extensions.getByType<JavaToolchainService>().launcherFor {
                        languageVersion = JavaLanguageVersion.of(8)
                    }

                    /*
                     * Carry over all system properties defined for test tasks into the Pitest tasks, except for the "junit"
                     * ones, as they can interfere with test stability.
                     */
                    systemProperties(
                        tasks.getByName<Test>("test").systemProperties.filterKeys {
                            !it.contains(
                                "junit"
                            )
                        }
                    )

                    /*
                     * Include a "pitest" system property to be able to run tests differently if necessary. Use sparingly!
                     */
                    systemProperty("pitest", "true")

                    outputs.cacheIf { true }
                }
                apply(plugin = "net.ltgt.errorprone")
                dependencies {
                    "errorprone"("com.google.errorprone:error_prone_core:$errorProneVersion")
                }
            }
        }
    }
}
