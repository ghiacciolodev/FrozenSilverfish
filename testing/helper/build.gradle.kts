plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.248-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
