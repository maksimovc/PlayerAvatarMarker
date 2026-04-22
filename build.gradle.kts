import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

group = "dev.thenexusgates"
version = "1.5.0"

repositories {
    mavenCentral()
    maven(url = "https://maven.hytale.com/release")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val fastMiniMapJar = fileTree("../FastMiniMap/build/libs") {
    include("FastMiniMap-*.jar")
}.files.maxByOrNull { it.lastModified() }

dependencies {
    add("compileOnly", "com.hypixel.hytale:Server:+")
    if (fastMiniMapJar != null && fastMiniMapJar.exists()) {
        add("compileOnly", files(fastMiniMapJar))
    }

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("PlayerAvatarMarker-${version}.jar")
}
