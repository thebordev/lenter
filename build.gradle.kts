plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.theboringdevelopers"
version = "1.0.6"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    intellijPlatform {
        create("IC", "2024.1.7")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
        }

        changeNotes = """
            <h3>Version 1.0.6</h3>
            <ul>
                <li>🖼️ <b>Vector Drawable Editor Preview:</b> большой preview при открытии XML Vector Drawable файлов</li>
                <li>🔍 Zoom In/Out функционал для изменения размера preview (64-1024 px)</li>
                <li>📊 Информационная панель с размерами, viewport и количеством путей</li>
                <li>🎨 Красивый UI с checkerboard фоном для прозрачности</li>
                <li>⚙️ Настройка размера preview в Settings</li>
                <li>🔄 Автоматическое обновление preview при изменении файла</li>
                <li>📑 Вкладка "Preview" рядом с вкладкой "Text" для быстрого переключения</li>
            </ul> 
            
            <h3>Version 1.0.5</h3>
            <ul>
                <li>📝 <b>String Resource Preview:</b> отображение содержимого строковых ресурсов inline в коде</li>
                <li>🌍 Поддержка 10+ языков: default, ru, en, de, fr, es, it, pt, ja, zh</li>
                <li>⚙️ Настраиваемый приоритетный язык с автоматическим fallback на default</li>
                <li>🎨 Красивое оформление с адаптацией к светлой и темной теме</li>
                <li>🎨 <b>Color Preview:</b> поддержка предопределенных цветов (Color.Red, Color.Blue и т.д.)</li>
                <li>🖼️ <b>Drawable Preview:</b> поддержка vectorResource() наравне с painterResource()</li>
                <li>⚡ Интеллектуальное кэширование строковых ресурсов для быстрой работы</li>
                <li>🔧 Множество мелких улучшений и исправлений</li>
            </ul>

            <h3>Version 1.0.4</h3>
            <ul>
                <li>🖼️ <b>Drawable Preview:</b> визуальный предпросмотр изображений в дереве проекта и inline в коде</li>
                <li>✨ Поддержка resource функций: painterResource() и vectorResource()</li>
                <li>✨ Поддержка Android Vector Drawable (XML) с полноценным рендерингом SVG path команд</li>
                <li>🎯 Кросс-платформенная поддержка: Android (R.drawable.*) и Compose Multiplatform (Res.drawable.*)</li>
                <li>⚡  Интеллектуальное кэширование с автоматической инвалидацией при изменении файлов</li>
                <li>🎨 Поддержка форматов: PNG, JPG, JPEG, WebP, XML Vector Drawable</li>
                <li>📍 Inline preview рядом с вызовами painterResource() и vectorResource()</li>
                <li>⚙️ Гибкие настройки: включение/выключение preview в коде и дереве файлов раздельно</li>
                <li>🚀 Оптимизированная производительность с SoftReference для экономии памяти</li>
            </ul>

            <h3>Version 1.0.3</h3>
            <ul>
                <li>ComposeResourceDeclarationHandler: быстрый переход к ресурсам Jetpack Compose по клику</li>
                <li>Поддержка более ранних версий IntelliJ IDEA (начиная с 2024.1)</li>
            </ul>

            <h3>Version 1.0.2</h3>
            <ul>
                <li>PreviewColor для Compose: отображение и редактирование оттенков прямо в редакторе</li>
                <li>Новые настройки предпросмотра цветов и иконок в gutter</li>
            </ul>

            <h3>Version 1.0.1</h3>
            <ul>
                <li>Добавлена поддержка добавления комментариев в существующие задачи Jira из IDE</li>
                <li>Обновлены горячие клавиши для генератора баг-репортов и комментариев</li>
                <li>Расширена документация по настройке Ollama и интеграции с Jira</li>
            </ul>

            <h3>Version 1.0.0</h3>
            <ul>
                <li>KDoc generation for Kotlin classes, functions, and properties</li>
                <li>Batch comment generation for entire classes</li>
                <li>Bug report generator with Jira integration</li>
                <li>Editable templates with live preview</li>
                <li>Support for multiple Ollama models</li>
                <li>Confluence Wiki Markup formatting for Jira</li>
                <li>Personal Access Token authentication</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.release.set(17)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
