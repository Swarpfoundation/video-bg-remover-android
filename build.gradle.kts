import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

buildscript {
    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            android.set(true)
            ignoreFailures.set(false)
            outputToConsole.set(true)
            filter {
                exclude("**/generated/**")
                include("**/kotlin/**")
            }
        }
    }

    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        extensions.configure<DetektExtension> {
            config.setFrom(files("$rootDir/config/detekt.yml"))
            buildUponDefaultConfig = true
            allRules = false
        }
    }
}
