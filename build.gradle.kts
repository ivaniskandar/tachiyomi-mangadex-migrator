plugins {
    id("dev.fritz2.fritz2-gradle") version "0.10"
    kotlin("plugin.serialization") version "1.5.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    js(IR) {
        browser()
    }.binaries.executable()

    sourceSets {
        val ktorVersion = "1.5.4"
        val commonMain by getting {
            dependencies {
                val kotlinSerializationVersion = "1.2.0"
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerializationVersion")

                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")

                implementation("com.soywiz.korlibs.korio:korio:2.0.9")
            }
        }
        val jsMain by getting {
            dependencies {
                val fritz2Version = "0.10"
                implementation("dev.fritz2:core:$fritz2Version")
                implementation("dev.fritz2:components:$fritz2Version")

                implementation("io.ktor:ktor-client-js:$ktorVersion")
            }
        }

        all {
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
        }
    }
}