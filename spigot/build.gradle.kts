import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    id("mod-build")
    id("mod-publish")
    alias(libs.plugins.shadow)
}


tasks.assemble {
    dependsOn(tasks.shadowJar)
}


repositories {
    maven("https://libraries.minecraft.net/")
    maven("https://maven.wallentines.org/")
    mavenLocal()
}


dependencies {

    // SkinSetter
    api(project(":api"))
    api(project(":common"))
    api(libs.midnight.core.spigot)

    shadow(project(":common").setTransitive(false))
    shadow(project(":api").setTransitive(false))

    compileOnly(libs.midnight.cfg)
    compileOnly(libs.midnight.cfg.json)
    compileOnly(libs.midnight.cfg.binary)

    compileOnly("org.spigotmc:spigot-api:1.20.2-R0.1-SNAPSHOT")
    compileOnly(libs.jetbrains.annotations)

}


tasks.withType<ProcessResources>() {
    filesMatching("plugin.yml") {
        expand(mapOf(
                Pair("version", project.version as String),
                Pair("id", project.properties["id"] as String))
        )
    }
}
