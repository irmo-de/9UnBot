plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "de.irmo.a9unbot"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.irmo.a9unbot"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

}

dependencies {

    implementation(kotlin("stdlib"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.work:work-runtime:2.9.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


    implementation("com.polidea.rxandroidble2:rxandroidble:1.17.2")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation ("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation ("org.bouncycastle:bcprov-jdk15on:1.70")
}