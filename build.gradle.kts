plugins {
    id("java")
    id("me.champeau.jmh") version "0.6.6"
    id("com.diffplug.spotless") version "6.4.2"
}

group = "nl.jrdie.graphql"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.graphql-java:graphql-java:18.0")
    implementation("org.ow2.asm:asm:9.3")
    implementation("org.ow2.asm:asm-commons:9.3")
    implementation("org.ow2.asm:asm-util:9.3")
    testImplementation("ch.qos.logback:logback-classic:1.2.11")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    jmhImplementation("org.openjdk.jmh:jmh-core:1.34")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.34")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

spotless {
    java {
        googleJavaFormat("1.15.0")
    }
}
