#!/usr/bin/env bash
# Wymaga: GH_TOKEN z scope repo. Publikuje v1.1 na NiewidzialnySI/Myring.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-/tmp/myring-release/myring-v1.1.apk}"
TAG="v1.1"

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "Ustaw GH_TOKEN (PAT z scope repo)" >&2
  exit 1
fi

cd "$ROOT"
git remote set-url github "https://x-access-token:${GH_TOKEN}@github.com/NiewidzialnySI/Myring.git"
# Wypchnij tylko commit Myring + nasze zmiany (bez merge cloud empty, jeśli nie trzeba).
# Prościej: push main z obecnego drzewa — force-with-lease tylko gdy historie się rozjeżdżają.
if ! git push github main; then
  echo "Zwykły push nie przeszedł — próbuję force-with-lease (histories: cloud merge + Myring)" >&2
  git push --force-with-lease github main
fi

gh release create "$TAG" "$APK" \
  --repo NiewidzialnySI/Myring \
  --title "Moje Krążki $TAG" \
  --notes "$(cat <<'EOF'
## v1.1

- Naprawione powiadomienia / exact alarmy (bez wiszącego FGS)
- Widget odświeża się o północy i po zmianach cyklu
- Autoaktualizacja z GitHuba
- Czytelniejszy setup uprawnień i status na home
EOF
)"

echo "Release: https://github.com/NiewidzialnySI/Myring/releases/tag/$TAG"
