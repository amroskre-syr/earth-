plugins { id("com.android.application") }

android {
    namespace = "com.nerva.earthwallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nerva.earthwallpaper.full"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-standalone-video-match"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
