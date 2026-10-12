plugins {
    kotlin("jvm") version "2.4.21"
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

    // PostgreSQL, SQLite & H2 In-Memory JDBC
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.xerial:sqlite-jdbc:3.48.0.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Database Migrations (Flyway)
    implementation("org.flywaydb:flyway-core:11.2.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.2.0")

    // Security (Argon2 / Bouncy Castle)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

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
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
