plugins {
    java
    alias(libs.plugins.defaults)
    alias(libs.plugins.license)
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
    implementation(libs.hivemq.amazonKinesisExtension.customizationSdk)
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        "test"(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                implementation(libs.assertj)
                implementation(libs.mockito)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    manifest.attributes(
        "Implementation-Title" to project.name,
        "Implementation-Vendor" to "HiveMQ GmbH",
        "Implementation-Version" to project.version,
    )
}

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}
