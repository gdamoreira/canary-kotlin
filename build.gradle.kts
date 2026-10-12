plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "org.opentibia.canary"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Netty
    implementation("io.netty:netty-all:4.1.118.Final")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Logging (SLF4J + Logback)
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // SQLite & MariaDB/MySQL JDBC
    implementation("org.xerial:sqlite-jdbc:3.48.0.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Database Migrations (Flyway)
    implementation("org.flywaydb:flyway-core:11.2.0")
    implementation("org.flywaydb:flyway-mysql:11.2.0")

    // LuaJ scripting engine
    implementation("org.luaj:luaj-jse:3.0.1")

    // Testing (JUnit 5 + Mockk + AssertJ)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

application {
    mainClass.set("org.opentibia.canary.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
