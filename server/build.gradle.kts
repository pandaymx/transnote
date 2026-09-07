import com.diffplug.gradle.spotless.SpotlessExtension

buildscript {
    configurations.classpath {
        // gjf 1.30 需要 guava 33.4+（CollectCollectors）；解析漂移到 32.1.3 时
        // spotless google-java-format 报 NoClassDefFoundError，强制固定高版本
        resolutionStrategy {
            force("com.google.guava:guava:33.4.0-jre")
        }
    }
}

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    group = "com.transnote"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.configure<SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            // 版本锁定 1.30.0：JVM 25 要求 google-java-format >= 1.30.0
            // （1.28.0 在 JVM 25 下直接报 jvm-version 错误）；
            // 必须与 .tools/gjf-1.30.jar 一致，避免本地/CI 格式化漂移
            googleJavaFormat("1.30.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
