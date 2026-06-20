---
description: Cut a GitHub release — preflight, trigger the release workflow, watch it, and verify
argument-hint: [version, e.g. 1.6.0 — omit to bump the latest tag]
---

# Auto Release

Cut a versioned GitHub release for `aic` by driving the `Release` workflow
(`.github/workflows/release.yml`). That workflow builds the reactor, tags the
commit `v<version>`, generates release notes from the commit log, and publishes
a GitHub Release with both runnable jars (`aic-cli`, `aic-web`) attached.

**Key facts (don't fight them):**
- Versions live in the **git tag**, not the pom — the pom stays `1.0-SNAPSHOT`. Never bump pom versions.
- The repo is **trunk-based**: the workflow builds from the current `origin/main` HEAD, so anything not pushed will not be in the release.
- The release is **outward-facing and hard to undo** — confirm with the user before triggering.

## Workflow

1. **Resolve the version.**
   - Use `$ARGUMENTS` if given (strip any leading `v`).
   - Otherwise read the latest tag (`git tag --sort=-v:refname | head -1`), propose the next semver
     bump (default: minor), and confirm with the user. Pick the bump from the pending commits:
     `fix:` only → patch; any `feat:` → minor; breaking change → major.

2. **Preflight (abort and report if any fails).**
   - `git fetch --tags --prune`
   - On `main`: `git rev-parse --abbrev-ref HEAD` → `main`.
   - Working tree clean: `git status --porcelain` is empty.
   - Local is pushed: `git rev-list --count origin/main..HEAD` → `0` (no unpushed commits; the workflow
     builds `origin/main`). If non-zero, tell the user to let you push first.
   - Tag is free: `v<version>` is not already in `git tag`.

3. **Preview what will ship.** Show `git log --oneline <latest-tag>..origin/main` — the exact commits
   that will be in the release. **Get explicit user confirmation** before continuing.

4. **Trigger the workflow.** `gh workflow run release.yml -f version=<version>`

5. **Watch it.** Find the run (`gh run list --workflow=release.yml --limit 1`) and
   `gh run watch <run-id> --exit-status`. If it fails, fetch the failing step's log
   (`gh run view <run-id> --log-failed`) and report — do not retry blindly.

6. **Verify.** `git fetch --tags`, then `gh release view v<version>`:
   - tag `v<version>` exists, release is **not** draft/prerelease,
   - both assets present: `aic-cli-<version>.jar`, `aic-web-<version>.jar`,
   - notes are non-empty (the workflow's commit-log changelog).

7. **Report** the release URL and a one-line summary of what shipped. If the notes read thin
   (e.g. a single housekeeping commit), offer to curate them with
   `gh release edit v<version> --notes-file <file>`.

## Constraints

- NO triggering the release without user confirmation of the version + the commit preview
- NO editing pom versions — the tag is the version
- NO releasing with unpushed commits or a dirty working tree
- NO blind retries on workflow failure — read the failed step log first
- ALWAYS verify the tag, assets, and notes after the run completes
- ALWAYS report the final release URL

$ARGUMENTS
