package com.salatlock.app

enum class Prayer(val fr: String, val ar: String) {
    FAJR("Fajr", "الفجر"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء");

    companion object {
        fun of(name: String?): Prayer? = values().firstOrNull { it.name == name }
    }
}
