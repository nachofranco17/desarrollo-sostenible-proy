#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$ROOT/backend/target/xperience.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Primero ejecutá mvn package en backend." >&2
  exit 1
fi
if [[ -z "${E2E_ADMIN_EMAIL:-}" || -z "${E2E_ADMIN_PASSWORD:-}" ]]; then
  echo "Faltan E2E_ADMIN_EMAIL o E2E_ADMIN_PASSWORD." >&2
  exit 1
fi
export COMPANY_NAME='Empresa E2E'
export ADMIN_EMAIL="$E2E_ADMIN_EMAIL"
export ADMIN_NAME='Admin'
export ADMIN_SURNAME='Prueba'
export ADMIN_PASSWORD="$E2E_ADMIN_PASSWORD"
export PROVISION_ACTOR="${USER:-e2e}"
cd "$ROOT/backend"
java -jar "$JAR" --spring.profiles.active=dev,provision --spring.main.web-application-type=none
