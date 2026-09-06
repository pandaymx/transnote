plugins {
    java
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:identity"))   // WorkspaceService（任务归属）
    implementation(project(":modules:board"))      // BoardService（§8.4 建板）
    // Spring Boot 4 BOM 统一版本
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.data.jpa)
    // 契约 §8.1：Apache POI 解析 .docx
    implementation(libs.poi.ooxml)
    // LLM 客户端 / 抽取 JSON（§8.3）
    implementation(libs.jackson3.databind)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)   // Gradle 9 需显式提供 launcher
}
