#!/usr/bin/env bash
# Static packaging audit. An APK is never installed by this script.
set -euo pipefail
apk=$1
build_tools=$2
out=$3
expected=$4
mkdir -p "$out"
"$build_tools/aapt" dump badging "$apk" > "$out/badging.txt"
"$build_tools/aapt" dump permissions "$apk" > "$out/permissions.txt"
"$build_tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$out/manifest.txt"
sha256sum "$apk" > "$out/sha256.txt"

grep -q "^package: name='fr.jarvis.v3' " "$out/badging.txt"
# AndroidX's package-local declaration is not an Android permission.
permissions=$(grep '^uses-permission:' "$out/permissions.txt" |
    grep -v "^uses-permission: name='fr.jarvis.v3.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'$")
test "$permissions" = "uses-permission: name='android.permission.RECORD_AUDIO'"
grep -q 'android:allowBackup.*=(type 0x12)0x0$' "$out/manifest.txt"
grep -q 'android:usesCleartextTraffic.*=(type 0x12)0x0$' "$out/manifest.txt"

# Release packaging can shorten resource paths. Inspect the file actually linked by the manifest.
rules_id=$(sed -nE 's/.*android:dataExtractionRules\([^)]*\)=@(0x[[:xdigit:]]+).*/\1/p' "$out/manifest.txt")
test -n "$rules_id"
"$build_tools/aapt2" dump resources "$apk" > "$out/resources.txt"
mapfile -t rule_paths < <(awk -v id="$rules_id" '
    $1 == "resource" { rule = ($2 == id) }
    rule && $2 == "(file)" && $4 == "type=XML" { print $3 }
' "$out/resources.txt")
test "${#rule_paths[@]}" -eq 1
"$build_tools/aapt" dump xmltree "$apk" "${rule_paths[0]}" > "$out/extraction-rules.txt"

case "$expected" in
    debug) grep -q '^application-debuggable' "$out/badging.txt" ;;
    release) if grep -q '^application-debuggable' "$out/badging.txt"; then exit 1; fi ;;
    *) exit 2 ;;
esac

python3 - "$out/extraction-rules.txt" <<'PY'
import re
import sys
from pathlib import Path

rules = Path(sys.argv[1]).read_text()
expected_domains = {"root", "file", "database", "sharedpref", "external"}
blocks = re.split(r"(?m)^\s*E: (cloud-backup|device-transfer)\b[^\n]*\n", rules)
if len(blocks) != 5 or set(blocks[1::2]) != {"cloud-backup", "device-transfer"}:
    raise SystemExit("Missing or duplicate backup rule block")
for mode, body in zip(blocks[1::2], blocks[2::2]):
    tags = re.findall(r"(?m)^\s*E: ([\w-]+)", body)
    attributes = re.findall(r'(?m)^\s*A: (domain|path)="([^"]+)"', body)
    pairs = list(zip(attributes[::2], attributes[1::2]))
    domains = {domain for (first, domain), (second, path) in pairs if (first, second, path) == ("domain", "path", ".")}
    if tags != ["exclude"] * 5 or len(attributes) != 10 or domains != expected_domains:
        raise SystemExit(f"Incomplete or unexpected rules: {mode}")
PY
printf 'PASS %s: package, microphone only, backup flags, both extraction blocks and debuggable status\n' "$expected"
