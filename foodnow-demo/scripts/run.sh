#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Only load the local, user-controlled file. Never commit credentials.
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
if [[ -n "${FOODNOW_MAVEN_REPO:-}" ]]; then
  exec mvn "-Dmaven.repo.local=${FOODNOW_MAVEN_REPO}" spring-boot:run
else
  exec mvn spring-boot:run
fi
