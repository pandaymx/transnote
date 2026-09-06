plugins {
    alias(libs.plugins.spring.boot)
    java
}

dependencies {
    implementation(project(":modules:shared"))
    // Spring Boot 4 BOM（boot 插件内置，无需 dependency-management 插件）
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
}
