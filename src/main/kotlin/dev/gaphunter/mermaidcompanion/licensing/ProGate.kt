package dev.gaphunter.mermaidcompanion.licensing

/**
 * The one place that decides whether Pro features may run. Fail-closed:
 * only an explicit `true` opens it. `null` (the platform's LicensingFacade
 * isn't initialized yet) and `false` both keep it closed, so a Pro feature
 * can never run because a license check was inconclusive.
 *
 * The check is injectable so the gating can be tested without an IDE; the
 * default reads the real license.
 */
object ProGate {
    @Volatile
    var licenseCheck: () -> Boolean? = { if (DevSandbox.isForced()) true else CheckLicense.isLicensed() }

    fun isOpen(): Boolean = licenseCheck() == true

    /** Whether this installation had diagram themes before they moved to Pro. */
    @Volatile
    var legacyThemesCheck: () -> Boolean = { LegacyFreeFeatures.keepsThemes() }

    /** Whether this installation had SVG export before it moved to Pro. */
    @Volatile
    var legacySvgCheck: () -> Boolean = { LegacyFreeFeatures.keepsSvgExport() }

    /** Diagram themes: a license, or an installation that already had them. */
    fun allowsThemes(): Boolean = isOpen() || legacyThemesCheck()

    /** SVG export: a license, or an installation that already had it. PNG export needs neither -- it's free. */
    fun allowsSvgExport(): Boolean = isOpen() || legacySvgCheck()
}
