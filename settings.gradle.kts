pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
        mavenLocal()

        // Folia libreforge-gradle-plugin (com.willfp.libreforge-gradle-plugin:*-folia).
        maven("https://maven.pkg.github.com/MrNickax/libreforge-folia") {
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    ?: providers.gradleProperty("gpr.user").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    ?: providers.gradleProperty("gpr.key").orNull
            }
            content { includeGroup("com.willfp") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "EcoEnchants"

// Core
include(":eco-core")
include(":eco-core:core-plugin")
include(":eco-core:core-nms")
include(":eco-core:core-nms:v1_21_8")
include(":eco-core:core-nms:v1_21_10")
include(":eco-core:core-nms:v1_21_11")
include(":eco-core:core-nms:v26_1_1")
include(":eco-core:core-nms:v26_1_2")