import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.sqldelight) apply false
    // Baseline profile plugins
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.androidTest) apply false
    // Code quality plugins
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Apply ktlint to all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        ignoreFailures.set(true)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }
        filter {
            exclude { element ->
                val path = element.file.path
                path.contains("/build/") ||
                    path.contains("/generated/") ||
                    path.contains("composeResources") ||
                    path.contains("androidRelease/generated")
            }
        }
    }
}

// Apply detekt to all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
        ignoreFailures = true
        parallel = true
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.compose.rules)
    }

    tasks.withType<Detekt>().configureEach {
        // Set JVM target to 17 (max supported by detekt 1.23.x)
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
        }
        exclude {
            val path = it.file.path
            path.contains("/build/") ||
                path.contains("/generated/") ||
                path.contains("composeResources") ||
                path.contains("androidRelease/generated")
        }
    }

    tasks.withType<DetektCreateBaselineTask>().configureEach {
        // Set JVM target to 17 (max supported by detekt 1.23.x)
        jvmTarget = "17"
        exclude {
            val path = it.file.path
            path.contains("/build/") ||
                path.contains("/generated/") ||
                path.contains("composeResources") ||
                path.contains("androidRelease/generated")
        }
    }
}

