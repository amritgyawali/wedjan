plugins {
    java
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.wedjan"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Boot 3.4 manages Testcontainers 1.20.x, whose docker-java predates Docker
// Engine 29 (min API 1.44) — /info probes fail with HTTP 400. Pin both.
extra["testcontainers.version"] = "1.21.4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Argon2id password hashing (spring-security-crypto backend)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // UUIDv7
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    // S3-compatible object storage (Cloudflare R2 in prod, MinIO locally)
    implementation(platform("software.amazon.awssdk:bom:2.29.52"))
    implementation("software.amazon.awssdk:s3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.github.docker-java:docker-java-api:3.7.1")
    testImplementation("com.github.docker-java:docker-java-transport-zerodep:3.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
