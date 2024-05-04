plugins {
    id("mod-build")
    id("mod-publish")
}

dependencies {

    compileOnlyApi(libs.midnight.cfg)
    compileOnlyApi(libs.midnight.cfg.json)
    compileOnlyApi(libs.midnight.cfg.binary)
    compileOnlyApi(libs.midnight.lib)
    compileOnlyApi(libs.midnight.core)
    compileOnlyApi(libs.midnight.core.server)

    compileOnlyApi(libs.slf4j.api)

}