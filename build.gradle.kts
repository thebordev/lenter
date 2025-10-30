plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.theboringdevelopers"
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            <h3>Version 1.0.1</h3>
            <ul>
                <li>Добавлена поддержка добавления комментариев в существующие задачи Jira из IDE</li>
                <li>Обновлены горячие клавиши для генератора баг-репортов и комментариев</li>
                <li>Расширена документация по настройке Ollama и интеграции с Jira</li>
            </ul>

            <h3>Version 1.0.0</h3>
            <li>KDoc generation for Kotlin classes, functions, and properties</li>
            <li>Batch comment generation for entire classes</li>
            <li>Bug report generator with Jira integration</li>
            <li>Editable templates with live preview</li>
            <li>Support for multiple Ollama models</li>
            <li>Confluence Wiki Markup formatting for Jira</li>
            <li>Personal Access Token authentication</li>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
