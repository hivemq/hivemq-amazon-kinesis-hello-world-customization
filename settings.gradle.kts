rootProject.name = "hivemq-amazon-kinesis-hello-world-customization"

pluginManagement {
    plugins {
        id("com.github.hierynomus.license") version "${extra["plugin.license.version"]}"
        id("com.github.sgtsilvio.gradle.utf8") version "${extra["plugin.utf8.version"]}"
    }
}

if (file("../hivemq-amazon-kinesis-extension-customization-sdk").exists()) {
    includeBuild("../hivemq-amazon-kinesis-extension-customization-sdk")
}
