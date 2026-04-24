plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "dev.bubblechat"
version = "1.0.0"
description = "Folia-compatible bubble chat plugin for Minecraft 1.21"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://artifactory.papermc.io/artifactory/universe/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("BubbleChat-${project.version}-by-ntdotjsx.jar")
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }
}