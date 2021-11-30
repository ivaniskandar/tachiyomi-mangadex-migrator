plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    kotlin("plugin.serialization")
}

android {
    compileSdk = 29

    defaultConfig {
        applicationId = "xyz.ivaniskandar.ayunda"
        minSdk = 21
        targetSdk = 29
        versionCode = 6
        versionName = "1.0.5"
    }

    buildTypes {
        debug {
//            isMinifyEnabled = true
//            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.extra["compose_version"] as String
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.6.0-alpha02")
    implementation("androidx.appcompat:appcompat:1.3.0-rc01")
    implementation("androidx.compose.ui:ui:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.ui:ui-tooling:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.material:material:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.material:material-icons-extended:${rootProject.extra["compose_version"]}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:1.0.0-alpha04")
    implementation("androidx.activity:activity-compose:1.3.0-alpha07")

    implementation("tachiyomi.sourceapi:source-api:1.1")

    implementation("com.squareup.okio:okio:3.0.0")

    val accompanistVersion = "0.9.1"
    implementation("com.google.accompanist:accompanist-insets:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanistVersion")

    val kotlinSerializationVersion = "1.3.1"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerializationVersion")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    val roomVersion = "2.3.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xopt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
}