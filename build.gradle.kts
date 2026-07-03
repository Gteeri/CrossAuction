plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.crossauction"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    // SQLite backend for single-server testing (see database.type in config.yml).
    // Intentionally NOT relocated below: sqlite-jdbc's native library loader
    // looks up its bundled .so/.dll resources using hardcoded "org/sqlite/..."
    // paths, and relocating the package breaks that lookup.
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()

        relocate("com.zaxxer.hikari", "dev.crossauction.libs.hikari")
        relocate("com.mysql", "dev.crossauction.libs.mysql")
        relocate("redis.clients.jedis", "dev.crossauction.libs.jedis")
        relocate("org.apache.commons.pool2", "dev.crossauction.libs.commonspool2")
        relocate("com.github.benmanes.caffeine", "dev.crossauction.libs.caffeine")
        relocate("com.google.gson", "dev.crossauction.libs.gson")
    }

    build {
        dependsOn("shadowJar")
    }
}
