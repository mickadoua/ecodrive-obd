#!/usr/bin/env bash
# Builds the release AAB signed with the upload key and publishes it to a Play track, from the
# maintainer's build machine (no GitHub Actions workflow: this repository runs no CI).
#
# Usage: scripts/play-publish.sh [internal|alpha|beta|production] [--release-status draft|completed] [--listing]
#   internal (default) = internal testing, alpha = closed testing.
#   beta (open testing) and production are public: they require MICKA_APPROVED_ISSUE=<ID>, the
#   task that carries the maintainer's written approval for this version.
#
# MICKA_APPROVED_ISSUE is a procedural guard, not a technical control: the script only checks
# that the value looks like a task identifier (e.g. ECO-123). It does not, and cannot, verify
# that the approval exists. The real lock is elsewhere: only the CTO holds the Play service
# account key, and beta/production uploads require the owner's written approval on the task
# named here. The script makes no network call to check it.
#   --release-status: draft by default (Play only accepts drafts until the app is published).
#   --listing: also publishes the store listing (app/src/main/play).
#
# Secrets read from the environment, never printed:
#   <PREFIX>_UPLOAD_KEYSTORE_B64, <PREFIX>_UPLOAD_KEYSTORE_PASSWORD, <PREFIX>_UPLOAD_KEY_ALIAS,
#   <PREFIX>_UPLOAD_KEY_PASSWORD (PREFIX = $PLAY_SECRET_PREFIX, otherwise APP_SECRET_PREFIX
#   below), and PLAY_SERVICE_ACCOUNT_JSON (content of the service account key). The keystore and
#   the key go to a private temporary directory, deleted on exit.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_SECRET_PREFIX=ECODRIVE

die() { echo "play-publish: $2" >&2; exit "$1"; }

TRACK=internal; STATUS=draft; LISTING=0
while [ $# -gt 0 ]; do
    case $1 in
        internal|alpha|beta|production) TRACK=$1 ;;
        --release-status)
            [ $# -ge 2 ] || die 2 "--release-status needs a value: draft or completed"
            STATUS=$2; shift ;;
        --listing) LISTING=1 ;;
        *) die 2 "unknown argument '$1'" ;;
    esac
    shift
done
case $STATUS in draft|completed) ;; *) die 2 "--release-status must be draft or completed" ;; esac

if [ "$TRACK" = beta ] || [ "$TRACK" = production ]; then
    [[ ${MICKA_APPROVED_ISSUE:-} =~ ^[A-Z]+-[0-9]+$ ]] ||
        die 3 "track '$TRACK' is public: set MICKA_APPROVED_ISSUE=<ID>, the task with the maintainer's written approval"
    echo "play-publish: track $TRACK, approved on $MICKA_APPROVED_ISSUE"
fi

P=${PLAY_SECRET_PREFIX:-$APP_SECRET_PREFIX}
KS_B64=${P}_UPLOAD_KEYSTORE_B64 KS_PASS=${P}_UPLOAD_KEYSTORE_PASSWORD
KEY_ALIAS=${P}_UPLOAD_KEY_ALIAS KEY_PASS=${P}_UPLOAD_KEY_PASSWORD
missing=()
for v in "$KS_B64" "$KS_PASS" "$KEY_ALIAS" "$KEY_PASS" PLAY_SERVICE_ACCOUNT_JSON; do
    [ -n "${!v:-}" ] || missing+=("$v")
done
[ ${#missing[@]} -eq 0 ] || die 4 "missing ${missing[*]}"

# Publish a commit, not a working tree: the SHA printed below must be what was uploaded.
[ -z "$(git status --porcelain -- app gradle build.gradle.kts settings.gradle.kts gradle.properties)" ] ||
    die 5 "uncommitted changes in the app or the build files"

umask 077
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
printf '%s' "${!KS_B64}" | base64 -d > "$TMP/upload.keystore" 2>/dev/null ||
    die 4 "$KS_B64 is not valid base64"
printf '%s' "$PLAY_SERVICE_ACCOUNT_JSON" > "$TMP/play.json"
python3 -I - "$TMP/play.json" <<'PY' || die 4 "PLAY_SERVICE_ACCOUNT_JSON is not a service account key (JSON)"
import json, sys
try:
    key = json.load(open(sys.argv[1]))
except ValueError:
    sys.exit(1)
sys.exit(0 if isinstance(key, dict) and key.get("type") == "service_account" else 1)
PY

export UPLOAD_KEYSTORE_PATH="$TMP/upload.keystore"
export UPLOAD_KEYSTORE_PASSWORD="${!KS_PASS}" UPLOAD_KEY_ALIAS="${!KEY_ALIAS}" UPLOAD_KEY_PASSWORD="${!KEY_PASS}"
export PLAY_SERVICE_ACCOUNT_FILE="$TMP/play.json"
# Gradle only needs the paths above, not the raw secrets.
unset "$KS_B64" "$KS_PASS" "$KEY_PASS" PLAY_SERVICE_ACCOUNT_JSON

tasks=(:app:publishReleaseBundle --track "$TRACK" --release-status "$STATUS")
[ "$LISTING" = 0 ] || tasks+=(:app:publishReleaseListing)
echo "play-publish: $(git rev-parse --short HEAD) -> $TRACK ($STATUS)"
sh ./gradlew --no-daemon --console=plain "${tasks[@]}"

AAB=app/build/outputs/bundle/release/app-release.aab
CODES=$(find app/build/intermediates -path '*release*' -name available-version-codes.txt 2>/dev/null | head -1)
echo "play-publish: done. commit $(git rev-parse HEAD), track $TRACK, status $STATUS"
[ -z "$CODES" ] || echo "play-publish: versionCode $(head -1 "$CODES")"
sha256sum "$AAB"
