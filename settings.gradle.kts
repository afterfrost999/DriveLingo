pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK 저장소 (docs/pleos-sdk-status.md 참고). 현재 프로토타입은 SDK 의존성을 추가하지 않았다.
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "DriveLingo"
include(":app")
