package dev.gaphunter.mermaidcompanion.licensing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevSandboxTest {

    private val sandboxConfig = "C:\\Work\\mermaid-companion\\build\\idea-sandbox\\IU-2025.2.6.2\\config"
    private val installedConfig = "C:\\Users\\dev\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.2"

    @Test
    fun `the switch opens Pro inside a runIde sandbox`() {
        assertTrue(DevSandbox.isForced("true") { sandboxConfig })
        assertTrue("unix-style path too", DevSandbox.isForced("true") { "/home/dev/mermaid/build/idea-sandbox/IU/config" })
    }

    @Test
    fun `the property alone never opens Pro on an installed IDE`() {
        assertFalse(DevSandbox.isForced("true") { installedConfig })
    }

    @Test
    fun `a sandbox without the property stays closed`() {
        assertFalse(DevSandbox.isForced(null) { sandboxConfig })
        assertFalse(DevSandbox.isForced("false") { sandboxConfig })
        assertFalse(DevSandbox.isForced("TRUE") { sandboxConfig })
        assertFalse(DevSandbox.isForced("") { sandboxConfig })
    }

    @Test
    fun `the config path is only looked up when the property asks for it`() {
        var asked = false
        DevSandbox.isForced(null) { asked = true; sandboxConfig }
        assertFalse(asked)
    }
}
