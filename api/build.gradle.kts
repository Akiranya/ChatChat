import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("chatchat.base-conventions")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    api(libs.adventure.bukkit)
    api(libs.adventure.minimessage)
    api(libs.adventure.configurate)

    compileOnly(libs.paper)
}

tasks {
    withType<ShadowJar> {
        listOf("net.kyori",
            "io.leangen",
        ).forEach { relocate(it, "at.helpch.chatchat.libs.$it") }
    }
}
