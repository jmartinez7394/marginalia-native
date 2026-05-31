# Marginalia Native — CLAUDE.md
# Read this before every session. Every rule here is active.

## What This Is
Marginalia Native is a KMP Android application for e-ink devices.
It builds the AnimaChora — the living spatial structure of a user's
intellectual life, made from tesserae (individual units of genuine
encounter), owned permanently in plain markdown files.

## The Absolute Rules

### 1. The shared core contains ZERO Android imports. Ever.
If a class in shared/ imports android.*, androidx.*, or any
Android-specific library, it is an architecture violation.
Stop and fix it before continuing. No exceptions.

### 2. Settings over hardcoded values. Always.
Any value governing behaviour a user might want to control
goes in SettingsRegistry. Never hardcoded. If uncertain,
make it a Setting with userVisible = false.

### 3. Vault format is sacred.
Any change to files written to disk requires updating
marginalia-native-vault-contract.md BEFORE writing code.
Code cannot lead spec on vault format.

### 4. All AI calls go through AIProvider interface.
Never call any AI API directly from any feature.
The AIProvider abstraction is mandatory.

### 5. JSON source of truth, SVG derived, PNG in-memory only.
For ink notes: JSON is permanent, SVG is derived from JSON,
PNG exists only in memory for API calls and is never written to disk.

### 6. Normalised world coordinates only.
Screen pixels are never stored permanently.
All spatial positions use normalised floating-point coordinates.

## Module Dependency Rules
androidApp UI → androidApp platform ✅
androidApp platform → shared ✅
androidApp UI → shared ✅
shared → androidApp (anything) ❌ NEVER
shared → Android SDK ❌ NEVER

## Four Network Exit Points Only
1. AI Provider (user-configured, BYOK)
2. Sync (git remote, Syncthing peer)
3. Licence Server (licenses.marginalia.app)
4. Online Features module (opt-in, disabled by default)
Nothing else may make network calls.

## The Vocabulary
- AnimaChora: the living structure the practice builds (not "palace")
- Tessera/Tesserae: the individual units (not "tile")
- Territory: named spaces within the AnimaChora (not "room")
  Referenced by name only: the Library, the Workshop, the Practice
- Practice territory: first-class space for daily exercises

## Testing Rules
- Every session ends with ./gradlew test passing
- Every session ends with ./gradlew lint passing with zero new violations
- Shared core business logic: unit tests required
- No session is complete until both pass

## Architecture Documents Location
All spec documents are in _docs/ at the project root.
Read the relevant spec document before building any feature.
The pre-flight reading order is in marginalia-native-architecture.md.

## Shared Core Packages
com.marginalia.ai        — AI provider interface and registry
com.marginalia.animachora — AnimaChora and Territory data model
com.marginalia.canvas    — Tessera canvas system
com.marginalia.device    — DeviceCapabilities interface
com.marginalia.ink       — Ink note data model and Kintsugi
com.marginalia.model     — Shared data models
com.marginalia.scribe    — Scribe pipeline (no rasterisation here)
com.marginalia.settings  — SettingsRegistry and Setting<T>
com.marginalia.sync      — SyncManager interface
com.marginalia.vault     — Vault file I/O, note service, schema

## Build State Document
The build state document lives at: ../_docs/marginalia-native-build-state.md

Every session MUST update this document before marking itself complete.
Update format: append a new session entry with date, session number,
what was built, what was adapted, gate status, next session.
Never overwrite previous entries — always append.

## Architecture Documents
All architecture spec documents live at: ../_docs/

Before any session that touches a new feature area, read the relevant
spec document from ../_docs/ before writing any code.

Document list:
- marginalia-native-architecture.md — read first, always
- marginalia-native-decisions.md — 38 decisions with reasoning
- marginalia-native-vault-contract.md — every file written to disk
- marginalia-native-ai-provider-spec.md — AI provider interface
- marginalia-native-ink-note-spec.md — handwriting system
- marginalia-native-canvas-spec.md — tessera canvas system
- marginalia-native-eink-refresh-strategy.md — display refresh
- marginalia-native-scribe-pipeline-spec.md — transcription pipeline
- marginalia-native-sync-spec.md — vault sync
- marginalia-native-navigation-spec.md — navigation specification
- marginalia-native-reference-and-shelf.md — shelved items and future reference
