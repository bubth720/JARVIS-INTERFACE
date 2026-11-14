package fr.jarvis.v3.conv

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import fr.jarvis.nexus.store.NexusRow
import fr.jarvis.nexus.store.NexusSchema
import fr.jarvis.nexus.store.NexusSession
import fr.jarvis.nexus.store.NexusStorageException
import fr.jarvis.nexus.store.NexusStoragePort
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

/**
 * NEXUS storage over the SQLite embedded by AndroidX (androidx.sqlite:sqlite-bundled-android 2.5.2), approved by the
 * owner on 14/11/2026 after CI measured that the platform SQLite of API 29 and 35 lacks FTS5 (run 43609210897).
 * A SEPARATE adapter: NEXUS sources stay unchanged (FUSION-02 06ccf98), CORE databases keep the platform SQLite.
 * Same rules as nexus-android-store AndroidNexusStorage:
 *  - FTS5 (MATCH, fts5vocab instance, bm25) is probed on an in-memory database BEFORE any file is created;
 *  - an existing file is inspected by its 100-byte header only; an unknown or newer schema is refused;
 *  - a corrupt database is refused and kept (never deleted or reset);
 *  - one serialized connection, explicit transactions, cross-process import lease.
 */
class BundledNexusStorage private constructor(private val db: SQLiteConnection, private val file: File) : NexusStoragePort {
    enum class Failure { CAPABILITY_UNAVAILABLE, INCOMPATIBLE_VERSION, STORAGE_FAILURE }
    sealed interface Open { data class Ready(val storage: BundledNexusStorage, val sqliteVersion: String) : Open; data class Rejected(val reason: Failure) : Open }

    companion object {
        private val driver by lazy { BundledSQLiteDriver() }

        /** Real FTS5 primitives NEXUS uses (no compile-option string is trusted). */
        fun probeFts5(): Pair<Boolean, String> = try {
            driver.open(":memory:", SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE).use { c ->
                val version = c.one("SELECT sqlite_version()") { it.getText(0) } ?: "?"
                c.exec("CREATE VIRTUAL TABLE p USING fts5(body)")
                c.exec("INSERT INTO p(rowid,body) VALUES(1,'phare bleu phare')"); c.exec("INSERT INTO p(rowid,body) VALUES(2,'arbre vert')")
                check(c.one("SELECT rowid FROM p WHERE p MATCH '\"phare bleu\"'") { it.getLong(0) } == 1L)
                c.exec("CREATE VIRTUAL TABLE v USING fts5vocab(p, 'instance')")
                check((c.one("SELECT count(*) FROM v WHERE term='phare'") { it.getLong(0) } ?: 0L) == 2L)
                check((c.one("SELECT bm25(p) FROM p WHERE p MATCH 'phare'") { it.getDouble(0) } ?: 0.0) < 0.0)
                true to version
            }
        } catch (e: Exception) { false to (e.message ?: e.javaClass.simpleName) }

        fun open(privateDirectory: File, name: String = "nexus.db"): Open {
            require(name.matches(Regex("[A-Za-z0-9_-]+\\.db")) && privateDirectory.isDirectory)
            val file = File(privateDirectory.canonicalFile, name)
            if (file.canonicalFile != file.absoluteFile || File(file.parentFile, ".${file.name}.nexus-import.lock").let { it.canonicalFile != it.absoluteFile })
                return Open.Rejected(Failure.STORAGE_FAILURE)
            var connection: SQLiteConnection? = null
            return try {
                if (file.exists()) {
                    val header = ByteArray(100)
                    java.io.DataInputStream(file.inputStream()).use { it.readFully(header) }
                    if (!header.copyOfRange(0, 16).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))) return Open.Rejected(Failure.STORAGE_FAILURE)
                    if (java.nio.ByteBuffer.wrap(header, 60, 4).int !in 0..NexusSchema.CURRENT_VERSION) return Open.Rejected(Failure.INCOMPATIBLE_VERSION)
                }
                val (fts5, version) = probeFts5()
                if (!fts5) return Open.Rejected(Failure.CAPABILITY_UNAVAILABLE)
                connection = driver.open(file.path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
                connection.exec("PRAGMA foreign_keys=ON")
                val storage = BundledNexusStorage(connection, file)
                val userVersion = storage.readSession { it.query("PRAGMA user_version") { row -> row.getInt(1) }.single() }
                if (userVersion !in 0..NexusSchema.CURRENT_VERSION) { connection.close(); return Open.Rejected(Failure.INCOMPATIBLE_VERSION) }
                NexusSchema.migrate(storage)
                Open.Ready(storage, version)
            } catch (_: java.io.IOException) { connection?.close(); Open.Rejected(Failure.STORAGE_FAILURE) }
              catch (_: RuntimeException) { connection?.close(); Open.Rejected(Failure.STORAGE_FAILURE) }
        }

        private fun SQLiteConnection.exec(sql: String) = prepare(sql).use { it.step() }
        private fun <T> SQLiteConnection.one(sql: String, read: (SQLiteStatement) -> T): T? = prepare(sql).use { if (it.step()) read(it) else null }
    }

    private var inTx = false
    private val session = object : NexusSession {
        override fun execute(sql: String, vararg values: Any?): Int {
            db.prepare(sql).use { st -> bind(st, values); while (st.step()) Unit }
            return db.prepare("SELECT changes()").use { it.step(); it.getLong(0).toInt() }
        }
        override fun <T> query(sql: String, vararg values: Any?, map: (NexusRow) -> T): List<T> {
            require(values.none { it == null || it is ByteArray }) { "Query parameters must be scalar and non-null" }
            return db.prepare(sql).use { st ->
                bind(st, values); val row = Row(st); val out = ArrayList<T>()
                while (st.step()) out.add(map(row))
                out
            }
        }
    }

    private fun bind(st: SQLiteStatement, values: Array<out Any?>) = values.forEachIndexed { i, v -> when (v) {
        null -> st.bindNull(i + 1)
        is ByteArray -> st.bindBlob(i + 1, v)
        is Float, is Double -> st.bindDouble(i + 1, (v as Number).toDouble())
        is Number -> st.bindLong(i + 1, v.toLong())
        is Boolean -> st.bindLong(i + 1, if (v) 1 else 0)
        is String -> st.bindText(i + 1, v)
        else -> throw IllegalArgumentException("Unsupported SQL parameter")
    } }

    /** One-based columns, as NexusRow requires; valid only during the mapping callback. */
    private class Row(private val st: SQLiteStatement) : NexusRow {
        private val names by lazy { (0 until st.getColumnCount()).associateBy { st.getColumnName(it) } }
        private fun idx(name: String) = names[name] ?: throw IllegalArgumentException("No column $name")
        override fun getString(column: String): String? = getString(idx(column) + 1)
        override fun getString(column: Int): String? = if (st.isNull(column - 1)) null else st.getText(column - 1)
        override fun getInt(column: String) = st.getLong(idx(column)).toInt()
        override fun getInt(column: Int) = st.getLong(column - 1).toInt()
        override fun getLong(column: String) = st.getLong(idx(column))
        override fun getLong(column: Int) = st.getLong(column - 1)
        override fun getDouble(column: String) = st.getDouble(idx(column))
        override fun getDouble(column: Int) = st.getDouble(column - 1)
        override fun getObject(column: String): Any? = getObject(idx(column) + 1)
        override fun getObject(column: Int): Any? = when (st.getColumnType(column - 1)) {
            androidx.sqlite.SQLITE_DATA_NULL -> null
            androidx.sqlite.SQLITE_DATA_INTEGER -> st.getLong(column - 1)
            androidx.sqlite.SQLITE_DATA_FLOAT -> st.getDouble(column - 1)
            androidx.sqlite.SQLITE_DATA_BLOB -> st.getBlob(column - 1)
            else -> st.getText(column - 1)
        }
    }

    private fun <T> guarded(action: () -> T): T = try { action() } catch (e: NexusStorageException) { throw e } catch (e: Exception) {
        if (e is IllegalArgumentException || e is IllegalStateException) throw e
        throw NexusStorageException(e)
    }
    @Synchronized override fun <T> readSession(block: (NexusSession) -> T): T = guarded { block(session) }
    @Synchronized override fun <T> inTransaction(block: (NexusSession) -> T): T = guarded {
        check(!inTx) { "Nested transaction" }
        db.exec("BEGIN IMMEDIATE"); inTx = true
        try { val r = block(session); db.exec("COMMIT"); r }
        catch (e: Throwable) { runCatching { db.exec("ROLLBACK") }; throw e }
        finally { inTx = false }
    }
    @Synchronized override fun acquireImportLease(): AutoCloseable? {
        val sidecar = File(file.parentFile, ".${file.name}.nexus-import.lock")
        if (sidecar.canonicalFile != sidecar.absoluteFile) throw NexusStorageException(IllegalArgumentException("Symlink lease refused"))
        val handle = RandomAccessFile(sidecar, "rw")
        val lock = try { handle.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
        if (lock == null) { handle.close(); return null }
        return AutoCloseable { try { lock.release() } finally { handle.close() } }
    }
    override fun availableBytes(): Long = android.os.StatFs(file.parentFile!!.path).availableBytes
    @Synchronized override fun close() = db.close()
}
