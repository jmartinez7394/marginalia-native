plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.marginalia.shared"
        compileSdk = 36
        minSdk = 26
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // No dependencies yet — added as features are built
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
