plugins {
    id("mod-build")
    id("mod-publish")
    alias(libs.plugins.loom)
    alias(libs.plugins.shadow)
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
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveClassifier.set("dev")
        configurations = listOf(project.configurations.shadow.get())
        minimize {
            exclude("org.wallentines.*")
        }
    }
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)

        val id = project.properties["id"]
        archiveBaseName.set("${id}-${project.name}")
    }
}


repositories {
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots1"
        mavenContent { snapshotsOnly() }
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.wallentines.org/")
    mavenLocal()
}


dependencies {

    // SkinSetter
    api(project(":api"))
    api(project(":common"))

    shadow(project(":api").setTransitive(false))
    shadow(project(":common").setTransitive(false))

    // Minecraft
    minecraft("com.mojang:minecraft:1.20.5-pre1")
    mappings(loom.officialMojangMappings())

    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:0.15.9")

    // MidnightCore
    modApi(libs.midnight.core.fabric)

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