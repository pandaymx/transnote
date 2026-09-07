plugins {
    java
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:board"))   // 文档 → 看板（todo 块转卡片）
    // Spring Boot 4 BOM 统一版本
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson3.databind)   // Boot 4 原生 Jackson 3（content/properties JSON 校验）
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)   // Gradle 9 需显式提供 launcher
}
