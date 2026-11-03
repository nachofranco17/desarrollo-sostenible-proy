#!/usr/bin/env bash
# RS19: aplica la política de retención del almacén de registros de seguridad.
# Borra solo los eventos con más de N días (mínimo 365). Lo ejecuta el administrador del almacén;
# la aplicación no puede borrar eventos.
# Uso:
#   ./scripts/depurar-registro-seguridad.sh [usuario-administrador] [dias]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/xperience.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Primero compilá el backend: cd backend && ./mvnw package" >&2
  exit 1
fi
printf 'Contraseña del administrador del almacén (auditoría): '
stty -echo
IFS= read -r SECURITY_LOG_ADMIN_PASSWORD
stty echo
printf '\n'
export SECURITY_LOG_ADMIN_USER="${1:-registro_auditor}"
export SECURITY_LOG_ADMIN_PASSWORD
cd "$ROOT/backend"
java -jar "$JAR" --spring.profiles.active=depurar-registro --spring.main.web-application-type=none \
  "--app.security-log.retention-days=${2:-365}"
unset SECURITY_LOG_ADMIN_PASSWORD
