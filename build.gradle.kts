import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "3.3.2"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

configurations {
    create("asciidoctorExt")
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.graphql-java:graphql-java-extended-scalars:20.1")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // DB & Validation
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("jakarta.validation:jakarta.validation-api")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // AWS SDK
    implementation("software.amazon.awssdk:s3:2.25.67")
    implementation("software.amazon.awssdk:sqs:2.25.67")
    implementation("software.amazon.awssdk:sns:2.25.67")
    implementation("software.amazon.awssdk:dynamodb:2.25.67")
    implementation("software.amazon.awssdk:auth")

    // Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.archive:6.10.0.202406032230-r")
    implementation("org.apache.tika:tika-core:2.9.0")

    // S3 Blob Uploader
    implementation("org.commonmark:commonmark:0.21.0")

    // Spring REST Docs
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    "asciidoctorExt"("org.springframework.restdocs:spring-restdocs-asciidoctor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

val snippetsDir = file("build/generated-snippets")

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
}

tasks.named<AsciidoctorTask>("asciidoctor") {
    inputs.dir(snippetsDir)
    dependsOn("test")

    setSourceDir(file("src/docs/asciidoc"))
    setOutputDir(file("build/docs/asciidoc"))

    configurations("asciidoctorExt")

    attributes(mapOf("snippets" to snippetsDir))

    sources {
        include("**/index.adoc")
    }

    outputOptions {
        backends("html5")
    }
}

tasks.register<Copy>("copyRestDocs") {
    dependsOn("asciidoctor")
    from("build/docs/asciidoc")
    into("src/main/resources/static/docs")
}

tasks.named("bootJar") {
    dependsOn("copyRestDocs")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
