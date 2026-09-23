plugins { id("com.android.application") }

android {
    namespace = "com.nerva.earthwallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nerva.earthwallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-video-match"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
