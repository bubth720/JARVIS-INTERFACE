package com.jarvis.interfaceapp.storage

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.jarvis.interfaceapp.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.*
import java.util.UUID

object LayoutSerializer : Serializer<LayoutDocument> {
    override val defaultValue: LayoutDocument get() = LayoutCodec.defaults(fresh = true)
    override suspend fun readFrom(input: InputStream): LayoutDocument {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (output.size() <= LayoutCodec.MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, LayoutCodec.MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return LayoutCodec.decode(output.toByteArray())
    }
    override suspend fun writeTo(t: LayoutDocument, output: OutputStream) { output.write(LayoutCodec.encode(t)) }
}

/** Exactly one instance per file/process, owned by Application. No Activity references. */
class DataStoreLayoutRepository(private val file: () -> File, scope: CoroutineScope) : LayoutRepository {
    private val store = DataStoreFactory.create(serializer = LayoutSerializer, scope = scope, produceFile = file)
    override suspend fun load(): LoadedLayouts = withContext(Dispatchers.IO) {
        val doc = store.data.first()
        LoadedLayouts(doc.layouts, !doc.fresh, doc.issue)
    }
    override suspend fun save(layouts: Layouts): Unit = withContext(Dispatchers.IO) {
        store.updateData { current ->
            if (current.issue != null) throw IOException("Protected layout document")
            LayoutCodec.withLayouts(current, layouts)
        }
        Unit
    }
    override suspend fun reset(): Layouts = withContext(Dispatchers.IO) {
        val result = store.updateData { current ->
            if (current.issue != null) {
                // Preserve the original before replacement. Any archive failure aborts reset.
                val original = file()
                val archive = File(original.parentFile, "dashboard.recovery-${UUID.randomUUID()}.json")
                val temporary = File(archive.path + ".tmp")
                try {
                    FileOutputStream(temporary).use { output ->
                        original.inputStream().use { it.copyTo(output) }
                        output.fd.sync()
                    }
                    if (!temporary.renameTo(archive)) throw IOException("Cannot preserve original layout")
                } finally { temporary.delete() }
                LayoutCodec.defaults()
            } else LayoutCodec.withLayouts(current, defaultLayouts())
        }
        result.layouts
    }
}
