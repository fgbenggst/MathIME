plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.mathime.keyboard"
    compileSdk = 35
    defaultConfig { applicationId = "com.mathime.keyboard"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
