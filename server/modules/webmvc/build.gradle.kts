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
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)                    // 12.11.0 覆盖 BOM（BOM 12.4.0 不支持 PG18）
    implementation(libs.flyway.database.postgresql)     // Flyway 10+ PostgreSQL 支持独立模块
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
}
