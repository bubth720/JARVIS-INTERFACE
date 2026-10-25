package com.jarvis.interfaceapp.avatar.athena

import android.content.Context
import com.jarvis.interfaceapp.avatar.assets.readBounded
import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import android.util.AtomicFile
import com.jarvis.interfaceapp.avatar.v2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Private, bounded V2 store. One decoded pack per Application; no URI or network access retained. */
class AthenaResources(private val context:Context,private val bundled:()->InputStream={context.assets.open("athena-private/athena-review.zip")}) {
    private val mutex=Mutex()
    private val active=AtomicFile(File(context.filesDir,"athena/active-v2.zip"))
    private var cached:LoadedPremiumPack?=null
    suspend fun load():LoadedPremiumPack?=withContext(Dispatchers.IO) {mutex.withLock {
        cached?.let {return@withLock it}
        val candidates=listOf<()->InputStream>({active.openRead()},bundled)
        for(open in candidates) {
            try { return@withLock decode(open()).also {cached=it} }
            catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(_:Exception) { /* Preserve invalid source; try only the next premium source. */ }
        }
        null
    }}
    suspend fun importPack(input:InputStream):LoadedPremiumPack=withContext(Dispatchers.IO) {mutex.withLock {
        val bytes=input.use {it.readBounded(ResourceLimit.BUNDLE_BYTES)}
        val pack=decode(bytes.inputStream())
        active.baseFile.parentFile?.mkdirs()
        val output=active.startWrite()
        try {output.write(bytes);active.finishWrite(output)}catch(e:Exception){active.failWrite(output);throw e}
        cached=pack;pack
    }}
    private fun decode(input:InputStream):LoadedPremiumPack {
        val validated=PremiumPackLoader.validate(PremiumZipSource(input))
        require(validated.manifest.artGrade==ArtGrade.PREMIUM_CANDIDATE) {"DEMONSTRATION_NOT_PRIMARY"}
        require(validated.manifest.resolve(PremiumKey())!=null) {"NEUTRAL_REQUIRED"}
        return PremiumPackLoader.decode(validated)
    }
}
