# Development workflow

- Build and iterate the browser preview first, with TV and phone layouts.
- Do not build or release TV/mobile APKs until the user is satisfied with the web preview and explicitly requests APK packaging.
- Browser previews validate design and interaction; final Android playback, remote control, and device compatibility still require native-device validation when APK packaging is requested.
- Never show the legacy four-character Chinese product name (Unicode `U+6D77 U+5C9B U+5F71 U+9662`) in any page, screen, generated concept, or user-visible UI. Use the brand symbol alone unless the user explicitly supplies a different approved display name.

# Handoff between agents

- This repository is edited by more than one coding agent. Before starting work, read `HANDOFF.md`: it records what changed while another agent held the repository and which files to re-read.
- Never rely on an earlier snapshot of a listed file; re-read it from disk first.
- Keep history append-only: do not rebase, amend, or force-push commits that are already pushed.

# Project release rules

- Every newly versioned APK must remain in `dist/`; never overwrite or delete an older versioned APK.
- Keep release artifacts local to the project. Do not copy or back up APKs to SMB shares.
- Record and verify the local APK SHA-256 hash before reporting the release complete.
