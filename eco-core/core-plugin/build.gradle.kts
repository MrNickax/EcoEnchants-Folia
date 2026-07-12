group = "com.willfp"
version = rootProject.version

dependencies {
    compileOnly(fileTree("../../lib") {
        include("*.jar")
    }
    )
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.essentialsx:EssentialsX:2.19.7") {
        exclude("*", "*")
    }
    // Bedrock support: used only to detect Bedrock players and send native confirmation forms.
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
}

tasks {
    build {
        dependsOn(publishToMavenLocal)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
            artifactId = rootProject.name
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MrNickax/EcoEnchants-Folia")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (findProperty("gpr.key") as String?)
            }
        }
    }
}