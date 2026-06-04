# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project Overview

**Points** counts arbitrary things (days since quitting smoking, times meditated, times angry — anything by
count) and visualizes how the count changes over time. It is an **offline-first** Kotlin Multiplatform app:
you can increment/decrement without a network and it syncs when connectivity returns, with concurrent
multi-device edits merging additively.

**Monorepo structure**
- `core/domain/` — KMP, pure Kotlin (`commonMain` only). Entities, ports (interfaces), and `fun interface`
  use cases. **Zero** persistence/serialization/infrastructure imports.
- `core/database/` — KMP. SQLDelight schema + queries for the local append-only event ledger.
- `core/network/` — KMP. Ktor client + API service. Depends on `shared/contract`.
- `core/data/` — KMP. Repository implementations (combine database + network) and offline-first sync.
- `core/presentation/` — KMP. Decompose components + MVIKotlin stores (shared UI logic + navigation).
- `shared/contract/` — KMP. `@Serializable` wire DTOs shared by client and backend.
- `apps/android/` — Android app (Jetpack Compose) rendering Decompose components.
- `apps/ios/` — iOS app (SwiftUI) consuming the shared framework via SKIE.
- `backend/` — Ktor server (Netty); H2 in dev, PostgreSQL in prod.

> Status: early scaffolding (Milestone 1). Module list above is the target; some modules/commands below
> do not exist yet. Update this file as modules land.

## Core domain model — the ledger

Every increment/decrement is an immutable `PointEvent` appended to a ledger; it is never updated or
hard-deleted.

- `id` (client-generated UUID — makes sync an idempotent union)
- `pointTypeId` (which thing is being counted)
- `delta: Long` (+1 / -1 / arbitrary; a "reset" is a compensating delta event, not a deletion)
- `deviceId` (attribution) · `createdAt: Instant` (display/ordering)

**Current value** = `SUM(delta)` per `pointTypeId` — order-independent, so concurrent offline edits are
conflict-free (PN-counter style CRDT). Deleting a `PointType` is a tombstone event, never a destructive delete.
**Sync** is a batch endpoint: client uploads pending events since a cursor; server upserts by `id` and returns
events the client is missing; client unions them and recomputes values.

## Architecture practices (mandatory)

### 1. Domain purity & ports/adapters
`core/domain` defines port interfaces using domain types only. Outer modules (`core/database`, `core/network`)
implement them as adapters. The domain never imports from outer modules; only the DI/wiring layer knows both sides.

### 2. `fun interface` use cases — never `typealias`
Each operation is its own named `fun interface`, grouped thematically per file in `core/domain/commonMain`:

```kotlin
fun interface IncrementPoint {
    suspend operator fun invoke(pointTypeId: Uuid, delta: Long = 1): PointEvent
}
```

Do **not** use `typealias` for injected suspend types — they erase to the same JVM type and Koin will silently
overwrite bindings. `fun interface` gives each a distinct class. Callers (Decompose components / MVIKotlin stores)
inject *individual* use cases — never a whole repository. Factory functions in the outer module build them via SAM
and own main-safety:

```kotlin
fun incrementPoint(repo: PointRepository, io: CoroutineDispatcher): IncrementPoint =
    IncrementPoint { typeId, delta -> withContext(io) { repo.append(typeId, delta) } }
```

### 3. Dependency injection — Koin (client only)
Client DI is **Koin** (`io.insert-koin`, v4.x), with `koin-core` in `commonMain`. Each `core/*` module exposes a
Koin module; they are assembled at startup (`startKoin` on Android, a `KoinInitializer` invoked from iOS). The IO
dispatcher is injected via `named("io")`. **The backend uses manual DI** (a `StorageContainer` passed into
`configureXRoutes()`), not Koin.

### 4. Coroutine dispatcher convention
The callee owns main-safety. `withContext(Dispatchers.IO)` lives in the use-case factories (injected `named("io")`),
not in datasources or components. ViewModels/components never reference a dispatcher directly. Tests swap
`dispatcherModule` → `testDispatcherModule` (`UnconfinedTestDispatcher`) so `withContext` is a no-op.

### 5. Test-driven development (paramount)
Write tests that define expected behavior **before** implementing. A feature is not done until its tests pass.
Test **through the port interface**, not the concrete class. Tests live beside the code and are committed in the
**same atomic commit** as the implementation. If a class is hard to unit-test, that is a design smell — fix the
design. Coverage is measured with **Kover** (`./gradlew koverHtmlReport`), report-only for now (no CI gate yet),
but high coverage is expected.

## Backend conventions (mirrors `~/git/kevonym/api`, on Kotlin 2.x / Ktor 3.x)

Layering: `domain` (`Local*Storage` ports + plain models) → `db` (`Database*Storage` over raw JDBC, SQL as consts,
`withContext(Dispatchers.IO)`, `ResultSet.toX()` mappers) → `api` (`configureXRoutes(Application)`, `@Serializable`
DTOs separate from domain, services) → `plugins` (HTTP, Serialization, Authentication, RateLimiting, Databases, CSRF)
→ `config`. Manual DI via `StorageContainer`. HikariCP; H2 dev / Postgres prod auto-detected by JDBC URL. Hand-rolled
migrations (`Migration(version, description, statements)` + `MigrationRunner` + `schema_version` table). Compile-time
`AppBehaviorConfig` via `ServiceLoader` (dev/prod source sets) + runtime `Secrets` from env vars. Google OAuth via
server-side code exchange + JWKS verification + HTTP-only session cookies.

## Build & test commands

> Filled in as modules land. Run all Gradle commands from the repo root via the wrapper (`./gradlew`).

```bash
./gradlew build                       # build everything
./gradlew :core:domain:test           # KMP domain tests
./gradlew :backend:test               # backend tests (H2 in-memory)
./gradlew koverHtmlReport             # coverage report (report-only)
./gradlew :apps:android:installDebug  # install Android debug build
# iOS: open apps/ios in Xcode and run on a simulator (needs full Xcode)
```

## Code style & git

- **EOF newline:** every source file (`.kt`, `.kts`, `.swift`, `.xml`, `.toml`, `.md`) ends with exactly one LF.
- **Commits:** atomic, small-to-medium, self-contained and buildable, single-purpose. Do not batch unrelated changes.
  Tests ship in the same commit as the code they cover. End commit messages with the Co-Authored-By trailer.
- **Worktrees & PRs (autonomy guardrail):** **all feature work happens in a git worktree under `./.wt/`** — never edit,
  commit, or build for a task in the primary checkout. Start every task with
  `git worktree add .wt/<branch-name> -b <branch-name> origin/main` (branch fresh from `origin/main`), do the work there,
  push the branch, and open a PR from it with `gh pr create`. Remove the worktree (`git worktree remove .wt/<branch-name>`)
  once the PR merges. `.wt/` is gitignored. **Never commit, push, or merge directly to `main`, and never merge a PR
  autonomously** — PR creation, commits, and CI are fair game without asking; the merge is the one irreversible step and
  waits for a human. `main` is branch-protected (PR + passing CI required, force-push and direct pushes blocked for
  everyone including admins), so this is enforced server-side as well as by convention.
- **Secrets (PUBLIC REPO):** secrets only via env vars / gitignored local files — never committed. The `Secrets`
  object (backend) and gitignored token files (clients) are the only places secrets live. Scan staged diffs before
  every push.
