import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

group = "dev.thenexusgates"
version = "1.3.5"

repositories {
    mavenCentral()
    maven(url = "https://maven.hytale.com/release")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val fastMiniMapJar = layout.projectDirectory.file("../FastMiniMap/build/libs/FastMiniMap-2.1.5.jar").asFile

dependencies {
    add("compileOnly", "com.hypixel.hytale:Server:+")
    if (fastMiniMapJar.exists()) {
        add("compileOnly", files(fastMiniMapJar))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("PlayerAvatarMarker-${version}.jar")
}
