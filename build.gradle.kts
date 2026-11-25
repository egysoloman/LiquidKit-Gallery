import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

// A checkout in a cloud-synced folder can keep generated classes in a local directory.
val localSettings = providers.fileContents(layout.projectDirectory.file("local.properties")).asText.orNull
val localBuildRoot = localSettings?.let { text ->
    Properties().apply { text.reader().use(::load) }.getProperty("backdrop.localBuildRoot")
}
if (!localBuildRoot.isNullOrBlank()) {
    val output = rootProject.file(localBuildRoot)
    allprojects {
        val module = path.removePrefix(":").replace(':', '/').ifEmpty { "_root" }
        layout.buildDirectory.set(output.resolve(module))
    }
}
