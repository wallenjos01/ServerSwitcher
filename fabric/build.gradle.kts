plugins {
    id("mod-build")
    id("mod-publish")
    alias(libs.plugins.loom)
}


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

loom {
    runs {
        getByName("client") {
            runDir = "run/client"
            ideConfigGenerated(false)
            client()
        }
        getByName("server") {
            runDir = "run/server"
            ideConfigGenerated(false)
            server()
        }
    }

    mixin {
        add(sourceSets.main.name, "${rootProject.name}-refmap.json")
    }

}

tasks {
    build {
        dependsOn(remapJar)
    }
    jar {
        archiveClassifier.set("dev")
    }
    remapJar {
        archiveClassifier.set("")
        val id = rootProject.name
        archiveBaseName.set("${id}-${project.name}")
        inputFile.set(jar.get().archiveFile)
    }
}


dependencies {

    // SkinSetter
    api(project(":api"))
    api(project(":common"))

    include(project(":api").setTransitive(false))
    include(project(":common").setTransitive(false))

    // Minecraft
    minecraft("com.mojang:minecraft:1.20.5-rc2")
    mappings(loom.officialMojangMappings())

    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:0.15.10")

    // MidnightCore
    modApi(libs.midnight.core.fabric)
    modApi(include("org.wallentines:brigadier-argument-fix:1.0.0")!!)

    // JWT
    modApi(libs.midnight.proxy.jwt)
    include(libs.midnight.proxy.jwt)
}


tasks.withType<ProcessResources>() {
    filesMatching("fabric.mod.json") {
        expand(mapOf(
                Pair("version", project.version as String),
                Pair("id", rootProject.name))
        )
    }
}