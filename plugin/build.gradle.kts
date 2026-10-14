plugins {
    java
}

group = "vn.vietanhrpg"
version = "0.7.6"

repositories {
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.132-stable")
    // EliteMobs owns the server currency. The jar is shipped in the server template.
    compileOnly(files("../server-template/plugins/EliteMobs.jar"))
    // Optional at runtime. Used only when poker.room.schematic.enabled is true.
    compileOnly(files("../server-template/plugins/WorldEdit.jar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
