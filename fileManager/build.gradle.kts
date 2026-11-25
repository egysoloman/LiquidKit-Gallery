plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kyant.foldfiles"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.kyant.foldfiles"
        minSdk = 23
        targetSdk = 37
        versionCode = 18
        versionName = "1.6.10"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":liquidkit"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
}
