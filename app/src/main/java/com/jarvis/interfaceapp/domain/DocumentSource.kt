package com.jarvis.interfaceapp.domain

/** Display-only attribution supplied with a response. Never resolves a URI or grants access. */
data class DocumentSource(val title:String,val excerpt:String="") {
    init {require(title.isNotBlank() && title.length<=200 && excerpt.length<=1000 && '\u0000' !in title && '\u0000' !in excerpt)}
}
