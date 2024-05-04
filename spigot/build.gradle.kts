plugins {
    id("mod-build")
    id("mod-shadow")
    id("mod-publish")
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

    shadow(project(":common")) { isTransitive = false }
    shadow(project(":api")) { isTransitive = false }

    compileOnly(libs.midnight.core)
    compileOnly(libs.midnight.core.spigot)
    compileOnly(libs.midnight.cfg)
    compileOnly(libs.midnight.cfg.json)
    compileOnly(libs.midnight.cfg.binary)

    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")

    compileOnly(libs.jetbrains.annotations)
}