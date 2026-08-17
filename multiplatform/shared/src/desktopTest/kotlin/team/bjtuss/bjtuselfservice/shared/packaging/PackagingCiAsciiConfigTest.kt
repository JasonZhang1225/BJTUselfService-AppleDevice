package team.bjtuss.bjtuselfservice.shared.packaging

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * English-runner WiX light 311 is triggered when non-ASCII installer strings
 * enter the MSI string table. CI must feed ASCII name/description into the
 * same Gradle fields jpackage/WiX actually use.
 */
class PackagingCiAsciiConfigTest {
    @Test
    fun ciWorkflowFeedsAsciiInstallerNameAndDescription() {
        val workflow = File(findRepoRoot(), ".github/workflows/kmp-package.yml").readText()
        val name = envAssignment(workflow, "WINDOWS_PACKAGE_NAME")
        val description = envAssignment(workflow, "WINDOWS_PACKAGE_DESCRIPTION")
        assertNotNull(name, "WINDOWS_PACKAGE_NAME must be set in kmp-package.yml")
        assertNotNull(description, "WINDOWS_PACKAGE_DESCRIPTION must be set in kmp-package.yml")
        assertTrue(name.all(::isPrintableAscii), "CI package name must be printable ASCII: $name")
        assertTrue(
            description.all(::isPrintableAscii),
            "CI package description must be printable ASCII: $description",
        )
    }

    @Test
    fun windowsGradleBindsJpackageStringsToEnvBackedVals() {
        val gradle = File(findRepoRoot(), "multiplatform/windowsApp/build.gradle.kts").readText()
        assertTrue("packageName = windowsPackageDisplayName" in gradle)
        assertTrue("description = windowsPackageDescription" in gradle)
        assertTrue("System.getenv(\"WINDOWS_PACKAGE_NAME\")" in gradle)
        assertTrue("System.getenv(\"WINDOWS_PACKAGE_DESCRIPTION\")" in gradle)
    }

    private fun envAssignment(yaml: String, key: String): String? =
        Regex("""$key:\s*(.+)""").find(yaml)?.groupValues?.get(1)?.trim()?.trim('"', '\'')

    private fun isPrintableAscii(ch: Char): Boolean = ch.code in 0x20..0x7e

    private fun findRepoRoot(): File {
        var dir = File(".").canonicalFile
        repeat(8) {
            val found = File(dir, ".github/workflows/kmp-package.yml").isFile &&
                File(dir, "multiplatform/windowsApp/build.gradle.kts").isFile
            if (found) return dir
            dir = dir.parentFile ?: error("repo root not found from ${File(".").canonicalFile}")
        }
        error("repo root not found from ${File(".").canonicalFile}")
    }
}
