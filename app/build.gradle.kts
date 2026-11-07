import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val releaseCredentials = runCatching {
    val path = providers.gradleProperty("keystoreProperties").orNull
        ?: throw GradleException("Release signing requires an external keystore.properties file")
    fun externalFile(path: String): File {
        val file = File(path)
        if (!file.isAbsolute) throw GradleException("Release signing files must use absolute paths")
        val resolved = if (file.exists()) file.toPath().toRealPath() else file.canonicalFile.toPath()
        if (resolved.startsWith(rootDir.toPath().toRealPath())) {
            throw GradleException("Release signing files must be outside the repository")
        }
        return resolved.toFile()
    }
    val propertiesFile = externalFile(path)
    if (!propertiesFile.isFile) {
        throw GradleException("Release signing requires an external keystore.properties file")
    }
    val properties = Properties()
    try {
        propertiesFile.inputStream().use { properties.load(it) }
    } catch (_: Exception) {
        throw GradleException("Cannot read release signing properties")
    }
    if (listOf("storeFile", "storePassword", "keyAlias", "keyPassword").any { properties.getProperty(it).isNullOrBlank() }) {
        throw GradleException("Release signing requires storeFile, storePassword, keyAlias, keyPassword")
    }
    val key = externalFile(properties.getProperty("storeFile"))
    if (!key.isFile) throw GradleException("Release keystore does not exist")
    properties.setProperty("storeFile", key.path)
    properties
}

val validateReleaseCredentials by tasks.registering {
    doLast { releaseCredentials.getOrThrow() }
}
tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(validateReleaseCredentials)
}

android {
    namespace = "com.buhat.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.buhat.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("src/test/resources")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    signingConfigs {
        create("release") {
            releaseCredentials.getOrNull()?.let {
                storeFile = File(it.getProperty("storeFile"))
                storePassword = it.getProperty("storePassword")
                keyAlias = it.getProperty("keyAlias")
                keyPassword = it.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
