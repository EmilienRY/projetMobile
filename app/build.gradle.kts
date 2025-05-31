plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.superpixelapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.superpixelapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

android.sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

dependencies {
    implementation(libs.zoomlayout)
    implementation(libs.core.ktx)
    implementation(libs.core)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.material.v1100)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.room.common.jvm)
    implementation(libs.room.runtime.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
