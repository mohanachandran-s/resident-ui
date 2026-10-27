# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Playwright + Cucumber (BDD) UI automation suite for the MOSIP Resident Portal, run through JUnit5's
Platform Suite engine. Tests cover end-to-end browser flows: login via UIN/VID, view history, manage
VIDs, update data, share data, get personalised card, verify email/phone, track requests. Maven
artifactId stays `uitest-resident` (folder is `ui-test`).

## Commands

**Run the full suite:**
```
mvn test
```
Surefire is configured to only pick up `ResidentTestRunner` (a `@Suite` class with no logic of its
own — glue and the Cucumber plugin are configured via `junit-platform.properties`, not annotations),
which runs every `.feature` file under `src/test/resources/features/` through the Cucumber JUnit5
engine.

**Run a scenario subset** (by exact scenario name, case-insensitive, comma-separated):
```
mvn test -DscenarioToRun="Login with VID,Login with UIN"
```
or set `scenarioToRun=` in `resident.properties`; the `-D` flag wins if both are set.

**Run a single feature file from the IDE:** right-click the `.feature` file (uses
`cucumber.properties` for glue — the IntelliJ Cucumber plugin reads this, not
`junit-platform.properties`). Extent reporting still fires because it's wired through the
`@Before`/`@BeforeStep`/`@After` hooks on the glue path, not a plugin registered per-runner.

**Run in Docker:**
```
docker build -t uitest-resident .
docker run -e RESIDENT_CONFIG_PATH=/home/mosip/config/resident.properties \
  -v /path/to/local/resident.properties:/home/mosip/config/resident.properties \
  uitest-resident
```
The image builds a self-contained executable jar at build time (`mvn package` — `MainRunner`
driving the JUnit Platform Launcher directly, see Architecture below) and runs it with `java -jar`
at container start; no Maven/Surefire invocation happens at runtime (see `MainRunner` for why that
matters). In production, Rancher injects config entirely through plain environment variables
(ConfigMaps/Secrets) — the `RESIDENT_CONFIG_PATH` mount above is a local/Docker convenience only;
`ResidentConfigManager` reads that file directly if present (it can't be baked into the jar and
overwritten the way entrypoint.sh used to under `mvn test`).

## Configuration

Five-layer property loading (lowest → highest), done in `ResidentConfigManager.init()`:
1. `Kernel.properties` (apitest-commons jar — MOSIP-wide defaults)
2. `uitest.properties` (uitest-commons jar — UI/browser defaults)
3. `valueMapping.properties` (this module — default identity field values for dynamic UIN generation)
4. `resident.properties` (this module — env-specific overrides; **never commit this file**, it holds
   real secrets)
5. Environment variables

`resident.properties` documents its own layering rules in its header comment — only declare a key
there if it's genuinely absent upstream, blank upstream, or an intentional override.

## Architecture

### Execution flow

1. `ResidentTestRunner` (`runner/`) — JUnit5 `@Suite` entry point; no code, just annotations
   selecting the Cucumber engine and the `features/` classpath resource. `MainRunner` (`runner/`)
   is the production entry point (see Commands/Run in Docker) — it selects this class through the
   JUnit Platform Launcher API directly, exactly what Surefire does internally under `mvn test`,
   just without the fork.
2. `ResidentHooks` (`hooks/`) — suite lifecycle:
   - `@BeforeAll initSuite` — loads config, initializes `MosipTestDataManager`, starts the Extent
     report, starts the OTP listener (all via uitest-commons).
   - `@BeforeAll initTestData` — generates/validates the shared UIN + VIDs via `ResidentTestData`
     (skipped if `identityGenManual=yes`).
   - `@Before scenarioSetup` — staggers each parallel worker's *first* scenario
     (`scenarioStaggerMillis`) so threads don't all hit login/auth at the same instant; delegates the
     rest to `CucumberHooks`.
   - `@AfterAll teardownSuite` — delegates to `CucumberHooks`.
3. Cucumber runs each `.feature` file's scenarios against step definitions in `steps/`, which drive
   Playwright page objects in `pages/`.

### Parallel execution

`junit-platform.properties` fixes Cucumber's parallelism at 3 workers and declares
`exclusive-resources` locks (`SHARED_IDENTITY`) on the `login`, `updateMyData`, `verifyContact`, and
`getMyUin` tags — these scenarios mutate the one shared test identity, so they're serialized against
each other even though everything else runs in parallel.

### Key classes

| Class | Role |
|---|---|
| `ResidentTestRunner` | JUnit5 Suite entry point (no logic) |
| `MainRunner` | Production entry point — runs the suite via the JUnit Platform Launcher API (`java -jar`, no Maven/Surefire) |
| `ResidentHooks` | Suite/scenario lifecycle (`@BeforeAll`/`@Before`/`@BeforeStep`/`@After`/`@AfterAll`) |
| `ResidentConfigManager` | Five-layer config loading; `scenarioToRun` bridging; typed getters |
| `ResidentUtil` | Identity/UIN/VID generation against MOSIP APIs, via `apitest-commons`' `KernelAuthentication` |
| `ResidentTestData` | Ensures a ready UIN + VIDs exist before any scenario runs |
| `ResidentGlobalConstants` | Shared constant values |
| `pages/*Page` | Playwright page objects, one per Resident Portal feature |
| `steps/CommonSteps` | Login variants (UIN/VID/perpetual/temporary/one-time) and shared assertions |
| `steps/*Steps` | Feature-specific step definitions, one per `.feature` file |

Everything browser-, OTP-, auth-, and reporting-related (`UiConfigManager`, `CucumberHooks`,
`UiExtentReportManager`, `MosipTestDataManager`, `KernelAuthentication`) comes from `uitest-commons`
(and transitively `apitest-commons`) — this module only holds Resident-Portal-specific config, page
objects, and step definitions.

### Reports

`UiExtentReportManager` (uitest-commons) drives Extent reporting via the Cucumber hooks in
`ResidentHooks`. Output directory is `reportOutputDir` in `resident.properties` (default
`test-output/`, kept outside the Maven-managed `target/` tree). Optionally pushed to S3/MinIO when
`push-reports-to-s3=yes`.
