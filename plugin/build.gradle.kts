import dev.triumphteam.helper.papi
import dev.triumphteam.helper.triumphSnapshots

plugins {
    id("chatchat.base-conventions")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("me.mattstudios.triumph") version "0.2.8"
}

repositories {
    papi()
    triumphSnapshots()
    // towny
    maven("https://repo.glaremasters.me/repository/towny/")
    // dsrv + dependencies
    maven("https://m2.dv8tion.net/releases")
    maven("https://nexus.scarsz.me/content/groups/public")
    // supervanish, griefprevention
    maven("https://jitpack.io")
    // essentialsx
    maven("https://repo.essentialsx.net/releases/")
}

dependencies {
    implementation(projects.chatChatApi)

    implementation(libs.triumph.cmds)
    implementation(libs.configurate)
    implementation(libs.bstats)

    compileOnly(libs.paper)
    compileOnly(libs.papi)
    compileOnly(libs.towny) { isTransitive = false }
    compileOnly(libs.essentials) { isTransitive = false }
    compileOnly(libs.discordsrv) { isTransitive = false }
    compileOnly(libs.supervanish) { isTransitive = false }
    compileOnly(libs.griefprevention) { isTransitive = false }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }
    shadowJar {
        listOf(
            "dev.triumphteam",
            "org.spongepowered",
            "io.leangen",
            "org.yaml",
            "org.bstats"
        ).forEach { relocate(it, "at.helpch.chatchat.libs.$it") }

        archiveFileName.set("ChatChat-${project.version}.jar")
    }
    processResources {
        filesMatching("**/paper-plugin.yml") {
            expand(
                mapOf(
                    "version" to "${project.version}",
                    "description" to "DelucksChat 2.0 or smth like that",
                )
            )
        }
    }
    register("deployJar") {
        dependsOn(build)
        doLast {
            exec {
                commandLine("rsync", shadowJar.get().archiveFile.get().asFile.absoluteFile, "dev:data/dev/jar")
            }
        }
    }
}
