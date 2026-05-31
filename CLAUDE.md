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

---

## Kotlin Coding Standards

### Mandatory patterns
- Kotlin Flow, not LiveData or RxJava
- Coroutines, not AsyncTask or threads directly
- Sealed classes for state representation, not String constants or enums where sealed is clearer
- Data classes for models, not POJOs
- Extension functions over utility classes
- `by lazy` for expensive initialisation
- Constructor injection via Hilt — never service locator or singleton access

### Coroutine dispatchers — use the correct one
- `Dispatchers.Main` — UI updates ONLY
- `Dispatchers.IO` — file system reads/writes, network calls
- `Dispatchers.Default` — CPU-intensive work (SVG generation, coordinate math, JSON parsing of large files)
- Never block the main thread. StrictMode in debug builds will crash if you do.

### Resource management
- Bitmaps, WebViews, Canvas objects must be explicitly released
- Every component that creates expensive resources implements cleanup called from `ViewModel.onCleared()` or Compose `DisposableEffect`
- Memory leaks are caught by LeakCanary in debug builds — treat them as build failures

### Error handling
- Use sealed `Result<T, E>` types across module boundaries — never throw raw exceptions across boundaries
- Handle all sealed class variants explicitly — no catch-all that swallows specific errors
- AI provider errors: surface rate limit errors as retry UI, authentication errors as settings UI

---

## Android-Specific Rules

### Lifecycle
- All state lives in ViewModel — never in Activity or Fragment directly
- Assume the Activity will be destroyed at any moment — because Android will
- Configuration changes (rotation, font size, dark mode) trigger Activity recreation — design for this

### Background work
- WorkManager for any work that must complete even if user leaves the app
- No foreground services unless the user is actively using the feature
- No polling. No unnecessary wakeups. Battery respect is non-negotiable.

### Permissions — minimum viable
- INTERNET — for AI provider and sync only
- Storage — for vault access
- No camera permission — Scribe uses the photo picker
- No location, contacts, microphone, or any other permission
- Requesting an undocumented permission is an architecture violation

### StrictMode (debug builds)
- Disk reads on main thread → crash
- Network on main thread → crash
- These are not warnings. They are crashes. Fix them, do not suppress them.

---

## Common Claude Code Mistakes — Never Do These

### Architecture violations
❌ Adding any android.* or androidx.* import to any file in shared/
❌ Calling AIProvider directly from a feature — always go through AIProviderRegistry
❌ Writing a file to disk whose format is not in marginalia-native-vault-contract.md
❌ Hardcoding a numeric or string constant that should be a Setting
❌ Making a network call outside the four designated exit points
❌ Creating a new coroutine on Dispatchers.Main for anything other than UI updates

### Vault violations
❌ Changing a JSON schema without updating marginalia-native-vault-contract.md first
❌ Writing a JSON file without schemaVersion as the first field
❌ Storing screen pixel coordinates permanently (normalised world coordinates only)
❌ Writing a PNG file to disk permanently (PNG is in-memory only for API calls)
❌ Writing to a vault path not documented in the vault contract

### Session discipline violations
❌ Marking a session complete without running ./gradlew test
❌ Marking a session complete without running ./gradlew lint
❌ Adding a new dependency without checking it is KMP-compatible (for shared/ modules)
❌ Leaving TODO comments without a linked issue number
❌ Committing without updating the build state document

---

## Session Completion Checklist

A session is NOT complete until all of these pass:

1. `./gradlew test` — all tests pass, no new failures
2. `./gradlew lint` — zero new lint violations
3. No new Android imports in shared/ — verify with grep
4. No hardcoded values that should be Settings — check new numeric/string literals
5. No JSON files written without schemaVersion
6. Build state document updated — appended to ../_docs/marginalia-native-build-state.md
7. Changes committed with a meaningful commit message
8. `git push` completed

If any of these fail, the session is not complete. Fix and recheck.

---

## Recovery Patterns

### Build fails after a session
1. Read the error carefully — what file, what error?
2. If it is a small fix (missing import, type annotation): fix it, commit with `fix: [description]`
3. If it is a design error (session got the architecture wrong): revert the session's commits with `git revert`, mark session incomplete, surface to project owner before retrying
4. Never push a broken build to master

### Lint violations appear
1. Fix every new violation — do not add to the lint baseline without approval
2. Suppressing a lint warning with `@SuppressLint` requires a comment explaining why

### Test fails after passing
1. Do not delete or modify the failing test
2. Fix the implementation to make the test pass
3. If the test itself was wrong, surface to project owner before changing it

### Shared core boundary violated
1. Identify what Android import was added and why
2. Extract the capability to a platform interface in shared/
3. Move the Android-specific implementation to androidApp/platform/
4. This is never a "small fix" — it requires design work

### Session runs long (approaching context limit)
1. Commit whatever is complete and working
2. Write a clear handoff note in the build state document
3. The next session picks up from the committed state
4. Never leave uncommitted work when approaching context limits

---

## Gate Types — When Device Testing Is Required

**Green gate:** Automated only. `./gradlew test` + `./gradlew lint`. No device needed.

**Yellow gate:** Green gate + emulator testing. UI verified for functional correctness on the Pixel 6 emulator.

**Red gate:** Yellow gate + physical device testing. E-ink specific behaviour verified on the Boox by the project owner. A red gate session is NOT complete until the project owner confirms the device test passes.

**Phase gate:** Comprehensive device test at end of each phase. Full feature set used as a real user would, for one genuine session.
