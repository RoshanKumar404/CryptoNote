package com.example.cryptnote.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class RoomMigrationTest {

    @Test
    fun migration1_2_executesExpectedDdlStatements() {
        val executedSql = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        executedSql.add(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        assertEquals(1, CryptNoteDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, CryptNoteDatabase.MIGRATION_1_2.endVersion)

        CryptNoteDatabase.MIGRATION_1_2.migrate(dbProxy)

        assertEquals(6, executedSql.size)
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN envelope BLOB") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN format_version INTEGER") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN key_id BLOB") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN checksum BLOB") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN is_vault INTEGER") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS quarantine") })
    }
}
