plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val vehicleSource = providers.gradleProperty("drivelingo.vehicleSource").getOrElse("mock")
val voiceSource = providers.gradleProperty("drivelingo.voiceSource").getOrElse("simulated")
// Pleos STT/TTS 서버 모드용 앱 등록 정보. 저장소에 넣지 않는다(~/.gradle/gradle.properties 또는 -P 로 전달).
// 비어 있으면 On-device 엔진만 쓴다. 배포 빌드에서는 앱에 넣지 말고 백엔드에서 받아 와야 한다.
val pleosClientId = providers.gradleProperty("drivelingo.pleosClientId").getOrElse("")
val pleosClientSecret = providers.gradleProperty("drivelingo.pleosClientSecret").getOrElse("")

android {
    namespace = "com.drivelingo"
    compileSdk = 35

    // AAOS 표준 Car API(CarUxRestrictionsManager). 차량 이미지에만 존재하므로 컴파일 전용으로 참조하고
    // 런타임에는 존재 여부를 확인한 뒤 사용한다 (core/vehicle/car/CarUxRestrictionsReader.kt).
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.drivelingo"
        // Pleos Playground 배포 앱은 API 28 이상 필요 (signing key rotation)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-prototype"
        buildConfigField("String", "VEHICLE_SOURCE", "\"$vehicleSource\"")
        buildConfigField("String", "VOICE_SOURCE", "\"$voiceSource\"")
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"$pleosClientId\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"$pleosClientSecret\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SHOW_VEHICLE_CONSOLE", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "SHOW_VEHICLE_CONSOLE", "false")
            // 프로토타입: 로컬 실행용으로 debug 키로 서명한다. 배포 전 교체 필요.
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Pleos Vehicle SDK: 방향지시등·브레이크·경고등. car 모드에서만 객체를 만든다(core/vehicle/car/PleosVehicleReader.kt).
    implementation(libs.pleos.vehicle)
    // Pleos STT/TTS. voiceSource=pleos 에서만 객체를 만든다(core/speech/pleos/).
    implementation(libs.pleos.stt)
    implementation(libs.pleos.tts)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
