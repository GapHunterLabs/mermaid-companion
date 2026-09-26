package dev.gaphunter.mermaidcompanion.licensing

import com.intellij.openapi.application.PathManager

/**
 * Local testing only: lets `./gradlew runIde` open the Pro features without a
 * Marketplace license, which cannot exist for a sandbox IDE.
 *
 * It needs both the system property that build.gradle.kts passes to runIde
 * and an IDE whose configuration folder is a Gradle sandbox. An IDE
 * installed on a real machine keeps its configuration elsewhere, so the
 * property alone never opens Pro there.
 *
 * The sandbox lives under `.intellijPlatform/sandbox/` with the current
 * IntelliJ Platform Gradle Plugin (2.x) and under `idea-sandbox/` with the
 * older one; both count.
 */
object DevSandbox {
    const val PROPERTY = "mermaidcompanion.pro.dev"

    private val SANDBOX_FOLDERS = listOf("/.intellijPlatform/sandbox/", "/idea-sandbox/")

    fun isForced(
        property: String? = System.getProperty(PROPERTY),
        configPath: () -> String = { PathManager.getConfigPath() },
    ): Boolean {
        if (property != "true") return false
        val path = configPath().replace('\\', '/')
        return SANDBOX_FOLDERS.any { path.contains(it) }
    }
}
