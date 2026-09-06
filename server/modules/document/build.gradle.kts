plugins {
    java
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:identity"))
    // Spring Boot 4 BOM 统一版本
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)   // content/properties JSON 合法性校验
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)   // Gradle 9 需显式提供 launcher
}
