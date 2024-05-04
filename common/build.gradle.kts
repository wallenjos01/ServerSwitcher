plugins {
    id("mod-build")
    id("mod-publish")
}

dependencies {

    api(project(":api"))

    compileOnlyApi(libs.midnight.cfg)
    compileOnlyApi(libs.midnight.cfg.json)
    compileOnlyApi(libs.midnight.cfg.binary)
    compileOnlyApi(libs.midnight.lib)
    compileOnlyApi(libs.midnight.core)
    compileOnlyApi(libs.midnight.core.server)
    compileOnlyApi(libs.midnight.proxy.jwt)

    compileOnlyApi(libs.slf4j.api)

}