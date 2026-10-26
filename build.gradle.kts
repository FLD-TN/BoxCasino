plugins {
    java
}

group = "com.dex"
version = "0.16.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    // BoxCore GemService; provided by BoxCore at runtime, never shaded
    compileOnly(files("libs/boxcore-api-1.3.0.jar"))
    // Boombox API classes only (optional plugin, game music); the Boombox plugin provides them at runtime
    compileOnly(files("libs/boombox-api-3.6.14.jar"))
    // ZNPCsPlus API (optional plugin, dealer NPCs at the machines)
    compileOnly(files("libs/znpcsplus-api-2.1.0.jar"))

    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Paper provides the SQLite driver at runtime; tests need their own copy
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("BoxCasino-${project.version}.jar")
}

tasks.test {
    useJUnitPlatform()
}
