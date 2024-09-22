plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("filament-tools-plugin")
}

if (project.properties["filamentPluginEnabled"]?.toString()?.toBoolean() == true) {
    filamentTools {
        // Material generation: .mat -> .filamat
        materialInputDir.set(project.layout.projectDirectory.dir("src/main/materials"))
        materialOutputDir.set(project.layout.projectDirectory.dir("src/main/assets/materials"))
    }

    tasks.named("clean").configure {
        doFirst {
            delete("src/main/assets/materials")
            delete("src/main/assets/environments")
        }
    }
}

android {
    namespace = "com.tonecolor.slamandroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tonecolor.slamandroid"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    aaptOptions {
        noCompress += listOf("filamat", "ktx")
    }
    /*androidResources {
        noCompress += listOf("filamat", "ktx")
    }*/
}



dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Filament
    implementation(libs.filament.android)
    implementation(libs.filamat.android)
    implementation(libs.gltfio.android)
    implementation(libs.filament.utils.android)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}