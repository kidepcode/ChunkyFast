package dev.kidepcode.chunkyfast.skip

enum class SkipMode {
    NONE,
    REGION;

    companion object {
        fun parse(raw: String): SkipMode {
            return when (raw.trim().uppercase()) {
                "NONE" -> NONE
                "REGION" -> REGION
                else -> REGION
            }
        }
    }
}