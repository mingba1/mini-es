#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
MVN="$ROOT/.tools/maven/bin/mvn"
if [[ ! -x "$MVN" ]]; then
  echo "Maven not found at $MVN — download Apache Maven 3.9.x into .tools/maven" >&2
  exit 1
fi
exec "$MVN" "$@"
