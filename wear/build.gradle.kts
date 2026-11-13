plugins { id("com.android.application") }
android {
    namespace = "dev.murmur.companion"
    compileSdk = 36
    sourceSets.getByName("main").java.srcDir("../shared-ui/src/main/java")
    defaultConfig {
        applicationId = "dev.murmur.companion.beta"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0-beta.2"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    signingConfigs {
        create("distribution") {
            val path = System.getenv("MURMUR_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("MURMUR_STORE_PASSWORD")
                keyAlias = System.getenv("MURMUR_KEY_ALIAS")
                keyPassword = System.getenv("MURMUR_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (System.getenv("MURMUR_KEYSTORE") != null) signingConfig = signingConfigs.getByName("distribution") }
        release {
        signingConfig = signingConfigs.getByName("distribution")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    implementation("androidx.activity:activity:1.10.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0") }
