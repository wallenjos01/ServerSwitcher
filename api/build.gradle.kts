plugins {
    id("mod-build")
    id("mod-publish")
}

repositories {
    mavenCentral()
    maven("https://maven.wallentines.org/")
    mavenLocal()
}

dependencies {

    api(libs.midnight.cfg)
    api(libs.midnight.cfg.json)
    api(libs.midnight.cfg.binary)
    api(libs.midnight.lib)
    api(libs.midnight.core)
    api(libs.midnight.core.server)

    api(libs.slf4j.api)

}