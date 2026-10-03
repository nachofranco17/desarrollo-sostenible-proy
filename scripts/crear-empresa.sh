#!/usr/bin/env bash
# Alta local de empresa + Administrador (H2 / perfil dev).
# Uso:
#   ./scripts/crear-empresa.sh 'Empresa Demo' 'admin@example.test' 'Ana' 'Prueba'
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/xperience.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Primero compilá el backend: cd backend && ./mvnw package" >&2
  exit 1
fi
if [[ $# -ne 4 ]]; then
  echo "Uso: $0 <empresa> <correo> <nombre> <apellido>" >&2
  exit 1
fi
# Aviso: con el backend levantado, H2 puede fallar. Detenelo antes.
if lsof -iTCP:8081 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Hay un proceso en el puerto 8081. Detené el backend (Ctrl+C) y volvé a intentar." >&2
  exit 1
fi
printf 'Contraseña inicial del Administrador (12 a 128 caracteres): '
stty -echo
IFS= read -r ADMIN_PASSWORD
stty echo
printf '\n'
if [[ ${#ADMIN_PASSWORD} -lt 12 || ${#ADMIN_PASSWORD} -gt 128 ]]; then
  echo "La contraseña debe tener entre 12 y 128 caracteres." >&2
  exit 1
fi
export COMPANY_NAME="$1"
export ADMIN_EMAIL="$2"
export ADMIN_NAME="$3"
export ADMIN_SURNAME="$4"
export ADMIN_PASSWORD
export PROVISION_ACTOR="${USER:-local}"
cd "$ROOT/backend"
java -jar "$JAR" --spring.profiles.active=dev,provision --spring.main.web-application-type=none
unset ADMIN_PASSWORD
