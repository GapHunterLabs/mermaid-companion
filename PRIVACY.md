# Privacy Policy — Mermaid Companion

**Effective date:** 2026-10-26

Mermaid Companion is a Gap Hunter Labs plugin for IntelliJ Platform IDEs.
This policy is short because the plugin's design makes it short: there
is nothing to disclose beyond what's below.

## What this plugin collects

**Nothing.** Mermaid Companion does not collect, transmit, or sell
any data — no source code, no file contents, no usage analytics, no
telemetry, no crash reports, no personally identifiable information.

## What it keeps on your machine

To decide when to show its one-time rating prompt, the plugin keeps two values
in the IDE's own settings on your computer: whether you have answered the
prompt, and a list of up to 500 findings it has already counted. Each entry
in that list is a one-way fingerprint that cannot be turned back into a file
path. Versions before 2026.2.0 kept the file path and line of each finding
instead; 2026.2.0 deletes that older list the first time it counts a finding.
None of this is ever sent anywhere.

The plugin also keeps the date it first ran in your IDE (version 2026.2.0 or
later). It uses that date only to know whether this installation keeps
diagram themes and SVG export without a license, and never sends it
anywhere.

Settings you change for the plugin are saved in the IDE's settings, like any
other IDE setting, and are never sent anywhere.

## Network access

**None.** Mermaid Companion makes zero network calls during normal
operation. Every check, detection, and analysis it performs runs
entirely in-process, inside your IDE, against the code already open in
your editor. Nothing you write, open, or edit is ever sent anywhere.

## Third parties

None. Mermaid Companion has no third-party SDKs, no analytics libraries,
no ad networks, no external dependencies that phone home.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **gaphunterlabs@gmail.com**
