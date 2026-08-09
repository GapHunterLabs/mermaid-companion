package dev.gaphunter.mermaidcompanion.lang

import com.intellij.lang.Language

// ID must NOT be "Mermaid" -- JetBrains bundles its own native Mermaid
// support (com.intellij.mermaid) as of IU-262.9437.65 (2026.2 EAP) using
// that exact Language ID, which crashes plugin load with
// ImplementationConflictException ("Language with ID 'Mermaid' is
// already registered") the moment both plugins are installed together.
// Confirmed via JetBrains's own External Plugins Checker Tests
// (TeamCity build #369805, mermaid-companion-0.1.1 against IU-262.9437.65).
object MermaidLanguage : Language("MermaidCompanion")
