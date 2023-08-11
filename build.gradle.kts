plugins {
    java
    id("io.github.sgtsilvio.gradle.defaults")
    id("com.github.hierynomus.license")
}

group = "com.hivemq.extensions.amazon.kinesis.customizations"
description = "Hello World Customization for the HiveMQ Enterprise Extensions for Amazon Kinesis"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.hivemq:hivemq-amazon-kinesis-extension-customization-sdk:${property("hivemq-amazon-kinesis-sdk.version")}")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:${property("junit-jupiter.version")}"))
    testImplementation(platform("org.mockito:mockito-bom:${property("mockito.version")}"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core:${property("assertj.version")}")
    testRuntimeOnly("org.slf4j:slf4j-simple:${property("slf4j-simple.version")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    manifest.attributes(
        "Implementation-Title" to project.name,
        "Implementation-Vendor" to "HiveMQ GmbH",
        "Implementation-Version" to project.version
    )
}

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}
