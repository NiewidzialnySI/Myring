#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f signing/myring-release.jks ]]; then
  echo "Brak signing/myring-release.jks — patrz signing/README.md"
  exit 1
fi
./gradlew :app:assembleRelease --no-daemon
echo ""
echo "Gotowe: app/build/outputs/apk/release/app-release.apk"