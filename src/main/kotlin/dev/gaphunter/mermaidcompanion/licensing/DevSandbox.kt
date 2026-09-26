package dev.gaphunter.mermaidcompanion.licensing

import com.intellij.openapi.application.PathManager

/**
 * Local testing only: lets `./gradlew runIde` open the Pro features without a
 * Marketplace license, which cannot exist for a sandbox IDE.
 *
 * It needs both the system property that build.gradle.kts passes to runIde
 * and an IDE whose configuration folder is a Gradle sandbox
 * (`.../idea-sandbox/...`). An IDE installed on a real machine keeps its
 * configuration elsewhere, so the property alone never opens Pro there.
 */
object DevSandbox {
    const val PROPERTY = "mermaidcompanion.pro.dev"

    fun isForced(
        property: String? = System.getProperty(PROPERTY),
        configPath: () -> String = { PathManager.getConfigPath() },
    ): Boolean {
        if (property != "true") return false
        return configPath().replace('\\', '/').contains("/idea-sandbox/")
    }
}
