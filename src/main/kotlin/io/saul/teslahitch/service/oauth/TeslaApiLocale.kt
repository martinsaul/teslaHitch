package io.saul.teslahitch.service.oauth

enum class TeslaApiLocale(val url: String) {
    NAAP(url = "https://fleet-api.prd.na.vn.cloud.tesla.com"),
    EMEA(url = "https://fleet-api.prd.eu.vn.cloud.tesla.com"),
    CHINA(url = "https://fleet-api.prd.cn.vn.cloud.tesla.cn");

    companion object {
        private val localeMap = mapOf(
            "en-US" to NAAP,
            "en-CA" to NAAP,
            "en-AU" to NAAP,
            "ja-JP" to NAAP,
            "ko-KR" to NAAP,
            "en-GB" to EMEA,
            "de-DE" to EMEA,
            "fr-FR" to EMEA,
            "nl-NL" to EMEA,
            "no-NO" to EMEA,
            "sv-SE" to EMEA,
            "zh-CN" to CHINA,
        )

        fun map(locale: String): TeslaApiLocale = localeMap[locale] ?: NAAP
    }
}
