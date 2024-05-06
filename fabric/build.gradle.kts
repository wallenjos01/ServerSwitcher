plugins {
    id("mod-build")
    id("mod-shadow")
    id("mod-fabric")
    id("mod-publish")
}


dependencies {

    // SkinSetter
    api(project(":api"))
    api(project(":common"))

    shadow(project(":api")) { isTransitive = false }
    shadow(project(":common")) { isTransitive = false }

    // Minecraft
    minecraft("com.mojang:minecraft:1.20.6")
    mappings(loom.officialMojangMappings())

    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:0.15.11")

    // MidnightCore
    modApi(libs.midnight.core.fabric)
    modApi(include("org.wallentines:brigadier-argument-fix:1.0.0")!!)

    // JWT
    modApi(libs.midnight.proxy.jwt)
    shadow(libs.midnight.proxy.jwt) { isTransitive = false }
}


tasks.withType<ProcessResources>() {
    filesMatching("fabric.mod.json") {
        expand(mapOf(
                Pair("version", project.version as String),
                Pair("id", rootProject.name))
        )
    }
}