# Privacy Policy: Mermaid Companion

**Effective date:** 2026-11-24

Mermaid Companion is a Gap Hunter Labs plugin for IntelliJ Platform IDEs.
This policy is short because the plugin's design makes it short: there
is nothing to disclose beyond what's below.

## What this plugin collects

**Nothing.** Mermaid Companion does not collect, transmit, or sell
any data: no source code, no file contents, no usage analytics, no
telemetry, no personally identifiable information. The one exception is an
error report that you choose to send from the IDE (see "Error reports").

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

A diagram can contain links (`click A "https://..."`). The preview never opens
them: a click on a linked node moves the caret to that node instead, and the
preview refuses to load any other page or open any window. Copying a diagram
(PNG, SVG or a Markdown code block) puts it on your clipboard and nowhere else.

## Error reports (only if you send them)

Mermaid Companion registers the IDE's standard JetBrains Marketplace error
reporter. When the IDE attributes an exception or a UI freeze to this plugin,
it can send a report to JetBrains Marketplace, where I can read it:

- Only when you send it from the IDE's error dialog, or when you have turned on
  automatic error reporting in the IDE settings.
- The IDE builds and sends the report, not this plugin. For an exception it
  carries the stack trace and the IDE and plugin versions; for a freeze, thread
  dumps and, when the IDE adds them, a performance snapshot and metrics; plus
  anything you write in the dialog.
- It is handled under JetBrains' privacy policy and the JetBrains Marketplace
  terms. I use these reports only to fix the plugin.

## Third parties

None inside the plugin. Mermaid Companion has no third-party SDKs, no
analytics libraries, no ad networks, no external dependencies that phone
home. An error report you choose to send goes to JetBrains Marketplace, as
described above.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **contact@gaphunterlabs.com**
