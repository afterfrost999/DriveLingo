package com.drivelingo.core.vehicle

/**
 * AAOS `android.car.drivingstate.CarUxRestrictions` 를 앱 내부 모델로 옮긴 값.
 *
 * 플래그 값은 AAOS 공개 상수와 동일하다. 앱의 나머지 코드는 android.car 에 의존하지 않고
 * 이 클래스만 본다 (실제 연동: core/vehicle/car/CarUxRestrictionsReader.kt).
 */
data class UxRestrictions(
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
) {
    fun has(flag: Int): Boolean = requiresDistractionOptimization && (activeRestrictions and flag) != 0

    companion object {
        const val BASELINE = 0
        const val NO_DIALPAD = 1
        const val NO_FILTERING = 2
        const val LIMIT_STRING_LENGTH = 4
        const val NO_KEYBOARD = 8
        const val NO_VIDEO = 16
        const val LIMIT_CONTENT = 32
        const val NO_SETUP = 64
        const val NO_TEXT_MESSAGE = 128
        const val NO_VOICE_TRANSCRIPTION = 256
        const val FULLY_RESTRICTED = 511

        val UNRESTRICTED = UxRestrictions(requiresDistractionOptimization = false, activeRestrictions = BASELINE)
        val DRIVING = UxRestrictions(requiresDistractionOptimization = true, activeRestrictions = FULLY_RESTRICTED)

        fun describe(flags: Int): List<String> = buildList {
            if (flags and NO_DIALPAD != 0) add("NO_DIALPAD")
            if (flags and NO_FILTERING != 0) add("NO_FILTERING")
            if (flags and LIMIT_STRING_LENGTH != 0) add("LIMIT_STRING_LENGTH")
            if (flags and NO_KEYBOARD != 0) add("NO_KEYBOARD")
            if (flags and NO_VIDEO != 0) add("NO_VIDEO")
            if (flags and LIMIT_CONTENT != 0) add("LIMIT_CONTENT")
            if (flags and NO_SETUP != 0) add("NO_SETUP")
            if (flags and NO_TEXT_MESSAGE != 0) add("NO_TEXT_MESSAGE")
            if (flags and NO_VOICE_TRANSCRIPTION != 0) add("NO_VOICE_TRANSCRIPTION")
        }
    }
}
