package dev.claudiocodigo.nexo.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Device-free guard that the explicit migration SQL matches the exported Room
 * schema v2 exactly. If the entity definitions evolve without updating the
 * migration, this test fails and forces the migration SQL to be kept in sync.
 */
class NexoDatabaseMigrationsConsistencyTest {

    @Test
    fun `migration1to2 sql matches exported schema v2`() {
        val v2Text = findSchema("2.json").readText()
        val v1TableNames = tableNames(findSchema("1.json").readText())

        // Each entity: `"tableName": "...", "createSql": "..."`.
        val entityRegex = Regex(
            "\"tableName\"\\s*:\\s*\"([^\"]+)\",\\s*\"createSql\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
        )
        // Only tables that are NEW in v2 (i.e. not present in the v1 schema)
        // belong in the migration.
        val tableSql = entityRegex.findAll(v2Text).mapNotNull { result ->
            val tableName = result.groupValues[1]
            if (tableName in v1TableNames) null
            else result.groupValues[2].replace("\${TABLE_NAME}", tableName)
        }.toList()

        // Index createSql entries contain "INDEX"; their table is encoded in the
        // index name `index_<table>_<column>`.
        val indexRegex = Regex("\"createSql\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*INDEX(?:[^\"\\\\]|\\\\.)*)\"")
        val indexSql = indexRegex.findAll(v2Text).map { match ->
            val sql = match.groupValues[1]
            val indexName = Regex("INDEX IF NOT EXISTS `([^`]+)`").find(sql)?.groupValues?.get(1).orEmpty()
            val table = indexName.removePrefix("index_").substringBeforeLast("_")
            sql.replace("\${TABLE_NAME}", table)
        }.toList()

        val expected = (tableSql + indexSql).toSet()
        val actual = NexoDatabaseMigrations.MIGRATION_1_2_SQL.toSet()

        assertEquals("Migration 1->2 SQL must match the exported schema v2", expected, actual)
    }

    private fun tableNames(text: String): Set<String> {
        val entityRegex = Regex("\"tableName\"\\s*:\\s*\"([^\"]+)\"")
        return entityRegex.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun findSchema(fileName: String): File {
        val candidates = listOf(
            File("schemas/dev.claudiocodigo.nexo.data.local.NexoDatabase/$fileName"),
            File("../schemas/dev.claudiocodigo.nexo.data.local.NexoDatabase/$fileName")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate schema file $fileName")
    }
}
