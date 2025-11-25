import buildlogic.Utils

plugins {
    id("build.library")
    id("build.fabric")
    id("build.shadow")
}

Utils.setupResources(project, rootProject, "fabric.mod.json")

dependencies {
    minecraft("com.mojang:minecraft:1.21.10")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.18.1")

    // Fabric API
    listOf(
        "fabric-api-base",
        "fabric-lifecycle-events-v1"
    ).forEach { mod ->
        modApi(fabricApi.module(mod, "0.138.3+1.21.10"))
    }

    implementation(libs.jwtutil)
    implementation(libs.smi)
    implementation(libs.midnightcfg)
    implementation(libs.midnightcfg.sql)
    implementation(libs.midnightcfg.codec.binary)
    implementation(libs.pseudonym)
    implementation(libs.pseudonym.lang)

    include(libs.jwtutil)

    modImplementation(libs.pseudonym.minecraft)
    modImplementation(libs.midnightcfg.minecraft)
    modImplementation(libs.cookieapi)
    modImplementation(libs.inventorymenus)
    modImplementation(libs.databridge)
}
