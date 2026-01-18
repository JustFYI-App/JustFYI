import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.aboutlibraries)
    kotlin("native.cocoapods")
}

kotlin {
    androidLibrary {
        namespace = "app.justfyi.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        // Enable Compose Multiplatform resources for Android
        androidResources.enable = true

        withHostTest {
            // Unit test configuration
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS targets configuration for Compose Multiplatform
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Just FYI Compose Multiplatform Shared Library"
        homepage = "https://justfyi.app"
        version = "1.0"
        ios.deploymentTarget = "15.0"
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "shared"
            isStatic = false
        }

        // Firebase pods - linkOnly to avoid cinterop issues with transitive dependencies
        pod("FirebaseCore") {
            version = "~> 12.0"
            linkOnly = true
        }
        pod("FirebaseAuth") {
            version = "~> 12.0"
            linkOnly = true
        }
        pod("FirebaseFirestore") {
            version = "~> 12.0"
            linkOnly = true
        }
        pod("FirebaseMessaging") {
            version = "~> 12.0"
            linkOnly = true
        }
        pod("FirebaseFunctions") {
            version = "~> 12.0"
            linkOnly = true
        }

        // SQLCipher for encrypted SQLite database
        pod("SQLCipher") {
            version = "~> 4.6"
            linkOnly = true
        }
    }

    // Suppress expect/actual beta warning
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            // Keep AndroidX navigation for Android-specific code if needed
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.coroutines.android)
            // SQLDelight Android driver
            implementation(libs.sqldelight.android.driver)
            // SQLCipher for database encryption
            implementation(libs.sqlcipher.android)
            // DataStore for preferences
            implementation(libs.androidx.datastore.preferences)
            // Tink for encryption (database key storage)
            implementation(libs.tink.android)
        }
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            // Material Icons Extended for all platforms (Compose Multiplatform)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // SQLDelight coroutines extensions
            implementation(libs.sqldelight.coroutines.extensions)

            // JetBrains Multiplatform Navigation (for shared navigation code)
            implementation(libs.jetbrains.navigation.compose)

            // ConstraintLayout for Compose Multiplatform
            implementation(libs.constraintlayout.compose.multiplatform)

            // GitLive Firebase SDK (multiplatform - shared across Android and iOS)
            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.messaging)
            implementation(libs.gitlive.firebase.functions)

            // AboutLibraries for open source license display
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                // SQLDelight SQLite driver for unit tests (JVM-only)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        iosMain.dependencies {
            // SQLDelight Native driver for iOS
            implementation(libs.sqldelight.native.driver)
        }
    }
}

metro {
    enabled = true
    debug = false
}

// SQLDelight configuration
sqldelight {
    databases {
        create("JustFyiDatabase") {
            packageName.set("app.justfyi.data.local")
            generateAsync.set(false)
        }
    }
}

// AboutLibraries configuration - outputs directly to Compose Resources
aboutLibraries {
    export {
        // Output directly to composeResources folder (recommended approach)
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        // Exclude generated field from output
        excludeFields.addAll("generated")
    }
}

dependencies {
    // Firebase BoM for version management (required for GitLive Firebase dependencies)
    "androidMainImplementation"(platform(libs.firebase.bom))
    "androidRuntimeClasspath"(libs.jetbrains.compose.ui.tooling)
}
