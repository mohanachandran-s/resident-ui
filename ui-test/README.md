# Resident Portal UI Automation

Playwright + Cucumber (BDD) UI automation suite for the MOSIP Resident Portal, run through JUnit5's
Platform Suite engine. Tests cover end-to-end browser flows: login via UIN/VID, view history, manage
VIDs, update data, share data, get a personalised card, verify email/phone, and track requests.

Maven artifactId stays `uitest-resident` (the folder is `ui-test`).

## Setup

`resident.properties` ships tracked in git with blank/default values. At minimum, fill in
`baseUrl`, `apiInternalEndPoint`, `keycloak-external-url`, and the three `*_client_secret` keys for
your target environment before running the suite — **never commit real values back into this
file**. Consider running `git update-index --skip-worktree src/test/resources/config/resident.properties`
locally so your filled-in values never show up in `git status`/`git diff` or get swept into a
commit by accident (`--no-skip-worktree` to undo).

## Run the full suite

```
mvn test
```

Surefire is configured to only pick up `ResidentTestRunner` (a `@Suite` class with no logic of its
own — glue and the Cucumber plugin are configured via `junit-platform.properties`, not annotations),
which runs every `.feature` file under `src/test/resources/features/` through the Cucumber JUnit5
engine.

## Run a scenario subset

By exact scenario name, case-insensitive, comma-separated:
```
mvn test -DscenarioToRun="Login with VID,Login with UIN"
```
or set `scenarioToRun=` in `resident.properties`; the `-D` flag wins if both are set.

## Run in Docker

```
docker build -t uitest-resident .
docker run -e RESIDENT_CONFIG_PATH=/home/mosip/config/resident.properties \
  -v /path/to/local/resident.properties:/home/mosip/config/resident.properties \
  uitest-resident
```

The image builds a self-contained executable jar at build time (`mvn package` — `MainRunner`
driving the JUnit Platform Launcher directly, see `AGENTS.md`) and runs it with `java -jar` at
container start; no Maven/Surefire invocation happens at runtime. In production, Rancher injects
config entirely through plain environment variables (ConfigMaps/Secrets) — the
`RESIDENT_CONFIG_PATH` mount above is a local/Docker convenience only; `ResidentConfigManager`
reads that file directly if present.

## Configuration

Five-layer property loading (lowest → highest), done in `ResidentConfigManager.init()`:
1. `Kernel.properties` (apitest-commons JAR — MOSIP-wide defaults)
2. `uitest.properties` (uitest-commons JAR — UI/browser defaults)
3. `valueMapping.properties` (this module — default identity field values for dynamic UIN generation)
4. `resident.properties` (this module — env-specific overrides; tracked with blank defaults, filled
   in locally per environment — **never commit real secret/URL values back into it**)
5. Environment variables

`resident.properties` documents its own layering rules in its header comment — only add a key
there if it's genuinely absent upstream, blank upstream, or an intentional override.

## Architecture

1. `ResidentTestRunner` (`runner/`) — JUnit5 `@Suite` entry point; no code, just annotations
   selecting the Cucumber engine and the `features/` classpath resource. `MainRunner` is the
   production entry point — it runs this suite via the JUnit Platform Launcher API directly
   (`java -jar`, no Maven/Surefire fork).
2. `ResidentHooks` (`hooks/`) — suite lifecycle: loads config, initializes test data, staggers each
   parallel worker's first scenario, and delegates reporting/teardown to `uitest-commons`.
3. Cucumber runs each `.feature` file's scenarios against step definitions in `steps/`, which drive
   Playwright page objects in `pages/`.

Everything browser-, OTP-, auth-, and reporting-related comes from `uitest-commons` (and
transitively `apitest-commons`) — this module only holds Resident-Portal-specific config, page
objects, and step definitions. See `AGENTS.md` for the full class-by-class breakdown and parallel
execution model.

## Reports

Extent reporting output goes to `reportOutputDir` in `resident.properties` (default `test-output/`,
kept outside the Maven-managed `target/` tree). Optionally pushed to S3/MinIO when
`push-reports-to-s3=yes`.

## License
This project is licensed under the terms of [Mozilla Public License 2.0](../LICENSE).
