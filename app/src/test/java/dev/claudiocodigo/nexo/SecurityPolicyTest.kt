package dev.claudiocodigo.nexo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository-wide security policy guard (Lote 7 / AUT-04, AUT-06).
 *
 * Fails if:
 * - the CalDAV/security/worker data layer logs anything (a secret could leak);
 * - any source file contains a hardcoded `Basic <base64>` Authorization literal;
 * - a hardcoded application-password assignment is present.
 */
class SecurityPolicyTest {

    private val mainSrcDir = File("src/main/java/dev/claudiocodigo/nexo")

    private val sensitiveDirs = listOf(
        "data/caldav", "data/security", "data/worker"
    ).map { File(mainSrcDir, it) }

    @Test
    fun `no logging in the sensitive data layer`() {
        val offenders = sensitiveDirs.flatMap(::kotlinFiles)
            .flatMap { file -> readLines(file).filter { it.contains("Log.") }.map { "${file.name}: $it" } }
        assertTrue("Logging in the sensitive data layer: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no hardcoded basic authorization literal`() {
        val regex = Regex("\"Basic [A-Za-z0-9+/=]{16,}\"")
        val offenders = mainSrcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> readLines(file).filter { regex.containsMatchIn(it) }.map { "${file.name}: $it" } }
            .toList()
        assertTrue("Hardcoded Authorization literal found: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no hardcoded application password assignment`() {
        val regex = Regex("password\\s*=\\s*\"[^\"]{6,}\"")
        val offenders = sensitiveDirs.flatMap(::kotlinFiles)
            .flatMap { file -> readLines(file).filter { regex.containsMatchIn(it) }.map { "${file.name}: $it" } }
            .toList()
        assertTrue("Hardcoded app password found: $offenders", offenders.isEmpty())
    }

    @Test
    fun `migration sql never references secrets`() {
        val schemaText = File("src/main/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrations.kt").readText()
        assertFalse(schemaText.contains("password"))
        assertFalse(schemaText.contains("Authorization"))
    }

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun readLines(file: File): List<String> =
        runCatching { file.readLines() }.getOrDefault(emptyList())
}
