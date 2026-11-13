plugins { id("com.android.application") }
android {
    namespace = "dev.murmur.companion"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.murmur.companion"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    buildTypes { release { signingConfig = signingConfigs.getByName("distribution") } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    implementation("androidx.activity:activity:1.10.1")
    implementation(project(":core"))
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
