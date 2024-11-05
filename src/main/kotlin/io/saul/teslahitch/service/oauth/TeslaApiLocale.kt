package io.saul.teslahitch.service.oauth

enum class TeslaApiLocale(val url: String) {
    NAAP(url = "https://fleet-api.prd.na.vn.cloud.tesla.com"),
    EMEA(url = "https://fleet-api.prd.eu.vn.cloud.tesla.com"),
    CHINA(url = "https://fleet-api.prd.cn.vn.cloud.tesla.cn");


    companion object {
        // TODO Map remaining locales.
        val mapOfLocale = mapOf(Pair("en-US", NAAP))
        fun map(locale: String) = mapOfLocale.getValue(locale)
    }
}