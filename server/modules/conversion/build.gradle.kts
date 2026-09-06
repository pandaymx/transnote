plugins {
    java
}

dependencies {
    implementation(project(":modules:shared"))
    // Spring Boot 4 BOM 统一版本
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    // 契约 §8.1：Apache POI 解析 .docx
    implementation(libs.poi.ooxml)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)   // Gradle 9 需显式提供 launcher
}
