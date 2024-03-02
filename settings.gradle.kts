pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        mavenLocal()
    }

    includeBuild("gradle/plugins")
}

rootProject.name = "serverswitcher"


include("api")
include("common")

include("fabric")
include("spigot")
