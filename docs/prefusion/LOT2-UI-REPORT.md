# UI-PF-01 / Lot 2 — non-actionable synthetic citation preview

Base: `mission/prefusion-gates-20261026` commit `82675ade011e69eb3bccd99efbf46d0697a3276b`.

Only additive class `fusion/UntrustedCitationPreview.kt`, its unit tests and this report. No changes to CORE transport, signing, avatar assets, UI identity, Android manifest or storage.

The component labels a well-formed page/offset/digest source **REVIEW_ONLY** and an invalid claim **UNKNOWN**, always non-clickable. It never opens a URI, asserts permission, manufactures a citation, renders a private path or promotes READY. This is not a citation renderer connected to NEXUS and not a security authentication implementation.

The existing Android CI still must execute JVM tests, APK build/lint, frozen scope checks and check no private art was embedded in the APK. Real Android instrumentation and physical performance are not claimed.

**Blockers:** CORE authenticated transport absent; full source/citation resolution and grant checks remain future work. **Rollback:** revert added files on this branch.
