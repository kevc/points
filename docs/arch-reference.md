# Architecture Reference

A portable description of how Points is built and **why**, written so another repo can copy the setup
without reading this codebase. Points is the worked example; replace "point", "event" and "type" with your
own domain nouns.

`CLAUDE.md` holds the binding rules for *this* repo. This file holds the reasoning and templates you would
take to a new one. Where they disagree, `CLAUDE.md` wins here.

---

## 1. What this architecture is for

- **Offline-first mobile app on Android and iOS, with one shared Kotlin core.** All business logic, state
  management, persistence, networking and navigation are written once in Kotlin Multiplatform (KMP). The
  native UIs (Jetpack Compose, SwiftUI) only render state and forward user actions.
- **The local database is the source of truth for the UI.** Writes never wait on the network; sync runs in
  the background and merges without conflicts.
- **A small Kotlin backend** (Ktor) whose main job is a single batch sync endpoint.
- **Built for test-driven, agent-assisted development.** Every boundary is an interface, every operation
  is a small named type, and work moves through small PRs that each pass CI on their own.

If your new app shares most of these traits, copy the setup. If it is online-only, you can drop the sync
machinery (§6) and keep everything else.

---

## 2. Guiding principles

1. **Dependencies point inward.** The domain depends on nothing. Outer layers implement interfaces
   (ports) that the domain defines. Only the DI layer sees both sides.
2. **One named type per operation.** Each use case is its own `fun interface`, so the call site, the
   tests and the DI graph all name exactly what is used.
3. **The callee owns main-safety.** Whatever does blocking work switches dispatcher itself. Callers never
   think about threads.
4. **Shared code produces finished output.** Stores emit ready-to-show strings and view state, so the
   two native UIs don't each reimplement formatting.
5. **Data is append-only, or last-write-wins with a timestamp.** Pick a conflict-free merge rule per
   entity up front. Never hard-delete; mark records deleted (tombstones) instead.
6. **Tests come first and go through the port.** If something is hard to test, fix the design rather
   than the test.
7. **Small, atomic, buildable commits and PRs.** A human merges; the agent does everything up to that
   point.

---

## 3. Module topology

```
apps/android   (Compose)               apps/ios  (SwiftUI, consumes the KMP framework via SKIE)
        \                                /
         core/presentation   Decompose components + MVIKotlin stores + Koin assembly + iOS entry point
                 |
         core/data           repositories (adapters), sync coordinator, use-case factories, platform glue
           /         \
 core/database     core/network ──► shared/contract ◄── backend   (Ktor server, JDBC)
 (SQLDelight)      (Ktor client)    (@Serializable DTOs)
           \         /
         core/domain         pure Kotlin: entities, ports, use-case interfaces, pure math
```

| Module | KMP? | Owns | Must not import |
|---|---|---|---|
| `core/domain` | yes, `commonMain` only | entities, port interfaces, `fun interface` use cases, pure functions (aggregation, ring/trend math) | anything for persistence, serialization, networking, DI or UI |
| `core/database` | yes | SQLDelight `.sq` schema and queries, migrations, `Local*DataSource` classes | network, presentation |
| `core/network` | yes | Ktor `HttpClient` factory (engine chosen per platform), `*ApiService` | database, presentation |
| `shared/contract` | yes | `@Serializable` wire DTOs only | domain types (keeps the wire format stable and free of platform-specific serializers) |
| `core/data` | yes | repository implementations, sync coordinator, use-case factories, connectivity monitor, DB driver factory, `dataModule` | presentation, UI |
| `core/presentation` | yes | Decompose root and children, MVIKotlin stores, dispatcher module, `initKoin`, iOS framework export | platform UI toolkits |
| `apps/android` | Android only | `Application` (starts Koin), `MainActivity`, Compose screens, theme | business logic |
| `apps/ios` | Swift | `@main` app, SwiftUI screens, theme, `Value<T>` → `ObservableObject` bridges | business logic |
| `backend` | JVM | Ktor server, storage ports, JDBC adapters, migrations | `core/*`; it shares only `shared/contract` |

Gradle rules:
- `settings.gradle.kts` includes each module and enables `TYPESAFE_PROJECT_ACCESSORS` (`projects.core.domain`).
- A `build-logic/` included build provides **convention plugins** (`<app>.kmp.library`,
  `<app>.android.application`). The KMP convention sets the Android target plus `iosArm64` and
  `iosSimulatorArm64`, JVM 17, SDK levels from the version catalog, and applies Kover. A module's own
  `build.gradle.kts` then only sets its namespace and dependencies.
- All versions live in `gradle/libs.versions.toml`. `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

Reference stack (versions current in this repo at the time of writing): Kotlin 2.1, AGP 8.7, Coroutines
1.9, kotlinx-serialization 1.7, kotlinx-datetime, Ktor 3.0 (client and server), SQLDelight 2.0,
Decompose 3.2, MVIKotlin 4.2, Koin 4.0, SKIE 0.10, Kover 0.9, Compose BOM 2024.12.

---

## 4. Domain layer (`core/domain`)

### Entities
Plain immutable `data class`es. IDs are `kotlin.uuid.Uuid` and are **generated on the client**, so
replaying the same record during sync is harmless. Timestamps are `kotlinx.datetime.Instant`.

### Ports
Interfaces written only in domain types. A repository is responsible for **identity and timestamps**:
callers pass a draft or a delta, and the repository assigns the id, device, `createdAt` and `updatedAt`.

```kotlin
interface PointRepository {
    suspend fun append(pointTypeId: Uuid, delta: Long): PointEvent
    fun observeValue(pointTypeId: Uuid): Flow<Long>
    fun observeAggregates(sinceMillis: Long): Flow<Map<Uuid, PointAggregate>>
    suspend fun sync()
}
```

Reads are `Flow`s that re-emit when the local database changes. One-shot reads are `suspend`.

### Use cases: `fun interface`, never `typealias`
```kotlin
fun interface IncrementPoint {
    suspend operator fun invoke(pointTypeId: Uuid, delta: Long): PointEvent
}
// A SAM method can't declare defaults, so offer the common case as an extension overload:
suspend operator fun IncrementPoint.invoke(pointTypeId: Uuid): PointEvent = invoke(pointTypeId, 1)
```
- **Why not `typealias`:** two `typealias`es for `suspend (Uuid) -> Unit` compile to the same JVM type,
  so Koin silently overwrites one binding with the other. Each `fun interface` is a distinct class.
- Group related use cases in one file per theme (`PointUseCases.kt`, `PointTypeUseCases.kt`).
- Components and stores inject **individual use cases, never a repository.**
- Read models are use cases too (`ObserveTiles`, `ObservePointTrend`). They combine several streams into
  one screen-ready model using pure domain functions that are easy to test.

---

## 5. Data layer (`core/data`): adapters and factories

### Use-case factories own main-safety
```kotlin
fun incrementPoint(repo: PointRepository, io: CoroutineDispatcher): IncrementPoint =
    IncrementPoint { typeId, delta -> withContext(io) { repo.append(typeId, delta) } }
```
`withContext(io)` appears **only** in these factories. It does not go in data sources, components or
stores. Factories that only return a `Flow` don't need a dispatcher.

### One repository can implement several ports
`OfflineFirstPointRepository : PointRepository, PointTypeRepository`. It is bound once in Koin and exposed
under each port (`single<PointRepository> { get<OfflineFirstPointRepository>() }`). This is useful when
both ports share one sync round trip.

### Platform glue via expect/actual
`DatabaseDriverFactory`, `ConnectivityMonitor`, the HTTP engine and the base URL come from an
`expect val platformDataModule: Module`. Each platform's `actual` supplies them.

---

## 6. Offline-first sync design

This is the most reusable part of the setup. Its rules:

### 6.1 Writes are local and marked pending
Every local write is an upsert with `pending = 1`. The UI observes the local database, so it updates
immediately, online or offline. **Repositories never call the network on write.**

### 6.2 Choose a merge rule (CRDT) per entity
| Entity kind | Rule | Example |
|---|---|---|
| Things that only accumulate (counts, logs, transactions) | **Append-only event ledger.** Merge = union by client id. Value = `SUM(delta)`, which gives the same result in any order (a PN-counter). A "reset" is a new event with the negative of the current value. | `point_event` |
| Records that are edited (names, settings, metadata) | **Last-write-wins register.** Each edit sets a new `updatedAt`; the newer one wins. Deletion sets `deletedAt` (a tombstone), and undo clears it. | `point_type` |

Never update or hard-delete ledger rows. Never hard-delete catalog rows.

### 6.3 Two watermarks per synced table
- **Push side:** the `pending` flag on each row, cleared once the server confirms it.
- **Pull side:** a cursor stored locally, set to the **server-assigned sequence number** (`seq`) and not
  to a timestamp, because device clocks drift. Each table gets its own cursor (`sync_cursor`,
  `type_cursor`), since edited records get a new `seq` on every edit while events never change.

Keep identity and cursors in a single-row `local_state` table, created once with `INSERT OR IGNORE`.

### 6.4 One batch endpoint, paged in both directions
`POST /sync` takes `{ ownerId, sinceSeq, events[], sinceTypeSeq, pointTypes[] }` and returns
`{ events[], nextSeq, pointTypes[], nextTypeSeq, hasMoreEvents, hasMoreTypes }`.
- The client uploads pending rows in chunks (500). The server returns at most a page of missing rows.
  It fetches `pageSize + 1` and trims the extra row, so one query also tells it whether more remain.
- The client loops until it has nothing left to push or pull. It advances cursors **after each round
  trip**, so an interrupted sync resumes where it stopped. If the server says "more" but the cursor
  didn't move, the client treats the sync as finished instead of looping forever.
- **Client: if one record from the server is malformed, skip it** (log it and still advance the
  cursor). Throwing would fail on every future pull and block sync permanently.
- **Server: validate the whole batch before writing anything.** A malformed record gets a 400 with no
  partial write. Bad client input is never a 500.
- Newer response fields have defaults, so older clients and servers still work together.

### 6.5 One sync driver, which is reactive
`SyncCoordinator` is the **only** component that starts a sync. It combines:
- connectivity (`ConnectivityMonitor.online`),
- the pending count (a reactive `COUNT(*) WHERE pending = 1`, summed across tables),
- the `SyncPointEvents` use case,

and exposes `Flow<SyncStatus>` (Offline, Syncing, Synced, Failed). On connect it always runs one pass,
because sync also *pulls* changes from other devices. A non-zero pending count triggers another pass. A
failure retries with exponential backoff that stops growing at a cap, and keeps retrying while online.
`collectLatest` cancels a pass in progress when the network drops or a newer edit arrives.

The coordinator takes no real network or UI, so tests drive it with fake flows, a fake use case and
virtual time.

### 6.6 Owner scoping from day one
Every row carries an `owner_id`, and every query filters by it, even though the client holds only one
owner. Before sign-in, the owner is an anonymous token generated per install. Sign-in later ties that
token to an account without rewriting any data. The server partitions all of its data by owner.

---

## 7. Local database (`core/database`)

- SQLDelight `.sq` files, one per table, with comments explaining the **why** of each query.
- Prefer one grouped, reactive aggregate query over N per-item queries. The home grid reads every
  item's totals in one scan, so a write re-emits once instead of once per tile.
- Keep `COALESCE` in SQL so aggregate results are never null.
- **Schema change ⇒ bump the database version and add a `.sqm` migration in the same commit.** Changing
  a `.sq` file without a version bump leaves existing installs with old tables, and the app crashes on
  launch.
- Data sources provide the identity they create (`ownerId`, `deviceId`) to sibling data sources through
  DI. Don't inject a second copy.

---

## 8. Network and wire contract

- `shared/contract` holds only `@Serializable` DTOs, used by both client and backend.
- **UUIDs and timestamps travel as strings** (ISO-8601), and enums travel as their names. Each side
  converts to its own types, so the contract needs no platform-specific serializers. Conversions that
  can fail are written as `toXOrNull()` functions.
- The `*ApiService` is a thin wrapper with one method per endpoint. Each platform supplies its HTTP
  engine (OkHttp on Android, Darwin on iOS). Tests use `ktor-client-mock`.

---

## 9. Presentation (`core/presentation`)

### Decompose for navigation and lifecycle
- One `RootComponent` holds a `childStack` of `Config`s, using a sealed `Child` type that wraps each
  screen component.
- Child components receive **navigation callbacks** (`onOpen`, `onBack`, `onSaved`) instead of a
  navigator, so the root owns all navigation.
- Persistent overlays (such as the sync status indicator) are separate children outside the stack.

### MVIKotlin store inside each component
```kotlin
class DefaultHomeComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    observeTiles: ObserveTiles,           // individual use cases, never a repository
    private val increment: IncrementPoint,
    mainContext: CoroutineContext,        // named("main"), injected; components never reference Dispatchers
    private val onOpenType: (Uuid) -> Unit,
) : HomeComponent, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore { storeFactory.homeStore(observeTiles, mainContext) }
    private val scope = CoroutineScope(SupervisorJob() + mainContext).also { s -> lifecycle.doOnDestroy(s::cancel) }
    override val state: Value<HomeStore.State> =
        MutableValue(store.state).also { v -> scope.launch { store.states.collect { v.value = it } } }
    override fun onIncrement(id: Uuid, step: Long) { scope.launch { increment(id, step) } }
}
```
- The component interface is what the UI depends on: `val state: Value<State>` plus `onX()` methods.
- The store observes read-model use cases and produces **finished view state**, including formatted
  text. Prefer navigation outputs over UI intents when the action only changes screen.
- State includes a `loaded` flag, so the UI can tell "still loading" apart from "genuinely empty".
- Pure geometry and formatting helpers (for example `TrendChartGeometry`) live here and are unit
  tested, so both native chart implementations only draw.

---

## 10. Dependency injection

**Client: Koin 4, with `koin-core` in `commonMain`.**
- Each layer exposes a `Module`: `dispatcherModule`, `dataModule` plus `platformDataModule`, and
  `presentationModule`. A shared `initKoin(appDeclaration)` assembles them.
  - Android: `Application.onCreate { initKoin { androidContext(this) } }`.
  - iOS: `startKoinIos()` and `createRootComponent()` live in `iosMain` and are called from the
    `@main` app's `init`.
- Dispatchers are injected **by name**: `named("io")` (`Dispatchers.IO`, or `Default` on Native) and
  `named("main")`.
- Repositories and coordinators are `single`. Use cases are `factory`.
- Components are `factory` with runtime parameters: `factory<HomeComponent> { (ctx, onOpen, onCreate) -> … }`,
  resolved with `parametersOf` by the root.
- Tests replace `dispatcherModule` with `testDispatcherModule` (`UnconfinedTestDispatcher`), so
  `withContext` doesn't actually switch threads. A **DI graph test** (`presentationModule` +
  `testDispatcherModule` + a fake data module) resolves every component, so a missing binding fails in CI
  instead of at runtime.

**Backend: manual DI.** A `StorageContainer` data class is passed into
`configureXRoutes(storage)`. Tests build their own container.

---

## 11. Platform apps

**Android:** a single `ComponentActivity` gets the root from Koin with
`retainedComponent { ctx -> get<RootComponent> { parametersOf(ctx) } }`, so it survives configuration
changes. It renders `root.stack` with Decompose's `Children(...)`, choosing the screen with a `when` over
the `Child` type. Window insets are applied once at the root. Resuming the app (`ON_RESUME`) triggers a sync. The theme lives in `ui/theme/` (Color, Type, Shape and an
app-specific accent palette).

**iOS:**
- `core/presentation` produces an iOS framework (`PointsKit`, dynamic, `-lsqlite3` for the SQLDelight
  native driver) with **SKIE** applied, so Swift sees `async/await`, real Swift enums and `AsyncSequence`.
- **`export()` the domain module** into the framework. Otherwise its top-level functions are dropped and
  its classes appear in Swift with a module-name prefix.
- Bridge `Value<State>` to SwiftUI with an `ObservableObject` that calls `state.subscribe { … }` and
  cancels in `deinit`. Write this bridge **once, as a generic helper**.
- SKIE turns Kotlin enums into Swift enums with lower-camel cases. Switches are exhaustive, with no
  `default`.
- Each Swift theme file matches its Android counterpart, so the two apps stay visually in step.

---

## 12. Backend

Layers (each one depends only on the layers above it in this list):
1. `domain`: `*Storage` port interfaces and plain models (`StoredEvent`) that carry server-only fields
   such as `seq`.
2. `db`: `Database*Storage` over raw JDBC, with SQL kept as constants, `withContext(Dispatchers.IO)` and
   `ResultSet.toX()` mappers. HikariCP. The database (H2 in dev and tests, Postgres in prod) is chosen
   from the JDBC URL.
3. **Migrations:** hand-rolled `Migration(version, description, statements)` objects, run by a
   `MigrationRunner` that tracks progress in a `schema_version` table. They run before any storage
   object is created.
4. `api`: `Application.configureXRoutes(storage)`, which maps DTOs to stored models with `toStoredOrNull()`.
5. `plugins`: logging, serialization and databases, with authentication, rate limiting and CSRF planned
   for when auth arrives.
6. `config`: compile-time `AppBehaviorConfig` loaded through `ServiceLoader` (separate dev and prod
   source sets) and runtime `Secrets` read from environment variables. Planned; not built in Points yet.

Split `Application.module()` (production wiring) from `Application.configureApp(storage, …)`, so tests
use `testApplication { application { configureApp(testStorage, syncPageSize = 2) } }`. Expose limits
such as page size as parameters, so paging can be tested with a few rows.

Planned auth: Google OAuth with the code exchange done on the server, JWKS verification and HTTP-only
session cookies. The authenticated user then replaces the `owner` parameter.

---

## 13. Testing and quality

- **TDD:** write the test that defines the behaviour first. A feature isn't done until its tests pass.
  Tests go in the same commit as the code.
- **Test through the port.** Use fakes of the port interfaces, not mocks of concrete classes.
- Where tests live:
  - `core/domain`: `commonTest`, pure functions and use cases over fakes.
  - `core/database` and `core/data`: `androidUnitTest`, using the JVM SQLite driver against a real
    in-memory database.
  - `core/data`: `commonTest` for the coordinator, using fake flows and virtual time (`runTest`,
    `advanceTimeBy`).
  - `core/presentation`: `commonTest` for stores, components and DI-graph resolution.
  - `core/network`: `ktor-client-mock`.
  - `backend`: `testApplication` plus H2 in memory.
- **Kover** for coverage (`./gradlew koverHtmlReport`). Report only at first; add a gate later.
- **CI (GitHub Actions):**
  - JVM and Android tests plus Kover run on Linux for every push and PR.
  - A macOS job links the iOS framework, gated by a path filter (`core/**`, `shared/**`, `apps/ios/**`,
    `build-logic/**`, `gradle/**`, root build files).
  - `concurrency` cancels superseded runs.
  - CI doesn't compile the Swift app, so verify Swift changes locally with `xcodebuild`.

---

## 14. Process conventions

- **Worktrees:** every task happens in `git worktree add .wt/<branch> -b <branch> origin/main`.
  `.wt/` is gitignored. Never edit in the main checkout.
- **PRs:** open with `gh pr create`. The body includes `Closes #N` for each issue the PR fully resolves.
  `main` is branch-protected (a PR and green CI are required; direct and force pushes are blocked). **A
  human does the merge.** Remove the worktree after merge.
- **Small PRs:** split a feature into shared logic, then Android, then iOS. Watch stacked PRs: confirm
  each one actually reached `origin/main`, because a PR merged into its parent branch may never get there.
- **UI PRs** embed a screenshot of the changed UI.
- **Atomic commits:** single purpose and buildable, with tests included. Commit messages end with the
  `Co-Authored-By` trailer.
- **Every source file ends with exactly one LF.**
- **Secrets:** the repo is public. Secrets come only from environment variables or gitignored local
  files. Scan the staged diff before every push.
- **`CLAUDE.md`** is the binding rule sheet for agents. Update it as modules land, and keep a status
  line about which parts are scaffolding.

---

## 15. Lessons learned (pitfalls to avoid)

| Pitfall | Rule |
|---|---|
| `typealias` use cases silently overwrite each other's Koin binding | Use a `fun interface` per operation |
| On Kotlin/Native, an **unhandled exception in a `launch` kills the app** (`trapOnUndeclaredException`). JVM tolerates the same code, so it only shows up on iOS | Wrap expected-to-fail calls (network, sync) started from a bare `launch` in stores or components: catch `Exception`, **rethrow `CancellationException`** |
| That same trap also fires for *synchronous* errors (for example a stale DB schema during DI setup) | Read the actual exception (`.ips` crash report or `simctl launch --console-pty`) before guessing the cause |
| A `.sq` change without a version bump crashes existing installs on launch | Bump the version and add a `.sqm` in the same commit |
| Repositories firing their own sync on each write, whose failures never reach the status UI, showed "Synced" while edits were stuck | One sync driver (the coordinator), started by the reactive pending count |
| A single malformed server record blocked sync forever | Clients skip and log malformed records; servers reject the whole batch with a 400 |
| Timestamp cursors miss rows when device clocks drift | Use a server-assigned `seq` cursor |
| An unbounded pull after weeks offline | Page both directions and advance cursors per round trip |
| Domain extension functions missing in Swift | `export()` the domain module into the iOS framework |
| `Value<T>` → SwiftUI bridge copy-pasted per screen | One generic `ObservableObject` bridge |
| "Today" computed with a timezone captured once and never updated | Read the clock and timezone live, and re-roll at local midnight with a `dayTicks` flow |

---

## 16. Adoption checklist for a new repo

1. Copy the root Gradle setup: `settings.gradle.kts` (typesafe accessors, `build-logic` included build),
   `gradle/libs.versions.toml` and the two convention plugins.
2. Create the modules in §3. Start with `core/domain` (entities, ports, use cases) and its tests.
3. Pick a merge rule per synced entity (§6.2). Write the `.sq` schema with `owner_id`, `pending` and a
   `local_state` table.
4. Write `shared/contract` DTOs (string ids and timestamps) and the `/sync` request and response with
   defaulted fields.
5. Build `core/data`: offline-first repository, use-case factories with `withContext(io)`,
   `SyncCoordinator`, and `dataModule` plus `platformDataModule`.
6. Build `core/presentation`: dispatcher module, root component, one component and store per screen,
   `initKoin`, iOS entry point, and the framework export with SKIE.
7. Make the apps thin: Android `Application` and `Activity`; iOS `@main` and a generic `Value` bridge.
8. Backend: storage ports, JDBC adapters, migrations, routes split from wiring, `StorageContainer`.
9. CI: Linux JVM job with Kover, plus a path-filtered macOS framework-link job. Turn on branch protection.
10. Write a `CLAUDE.md` with the mandatory rules (§2, §4, §5, §10, §13, §14) and a status line.
