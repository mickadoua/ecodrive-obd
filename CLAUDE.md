# CLAUDE.md

Working rules for anyone contributing to this repository on behalf of the maintainer.

## Repository

- `app/`: the Android app (Kotlin, JDK 17, Android SDK 36, target API 36).
- `site/`: the presentation page (French and English). Anything merged into `main` under `site/` is published to GitHub Pages by `.github/workflows/pages.yml`.
- `docs/`: Play Store material and screenshots.

Everything in this repository is in English: commit messages, pull request titles and descriptions, code comments, README. No secrets, tokens, private URLs or host names in the repository or in pull requests.

## Test locally, then push

There is no test CI. The only workflow is `pages.yml`, which publishes `site/` and runs no tests. Do not add a workflow, and never add an iOS CI on macOS runners, without the maintainer's written approval.

Every change is tested locally before it is pushed. The task or pull request states which commands were run, where, and their result. For the app:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

For the site, open the changed pages locally (both languages) and check links and layout on desktop and mobile widths.

A pull request does not wait for a green CI. An app change that nobody has built and tested locally is not mergeable: say so instead of presenting it as verified.

## Merging pull requests

The maintainer never merges pull requests. Merges are done by the agents: the CTO (Sacha) first, otherwise the adversarial reviewer (Victor) or QA (Quinn).

The rule is strict: nothing may break. Before every merge:

1. Tests and lint pass locally.
2. The build is verified (`./gradlew assembleDebug` for the app; the pages render for the site).
3. The rollback plan is known, usually a revert of the squashed commit through a new pull request.

At the slightest doubt, do not merge, and report it on the task.

A pull request opened by the CTO may be merged by the CTO when no other agent is available to review it.

`main` is protected with linear history: never push to it directly; merge with squash. Never push to a branch you did not create (the maintainer's branches start with `MQT/`). After a merge that touches `site/`, check that the Pages deployment succeeded and that the live page loads.
