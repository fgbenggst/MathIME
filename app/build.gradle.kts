plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.mathime.keyboard"
    compileSdk = 35
    defaultConfig { applicationId = "com.mathime.keyboard"; minSdk = 26; targetSdk = 35; versionCode = 41; versionName = "1.0.21" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("androidx.core:core:1.13.1")
}
