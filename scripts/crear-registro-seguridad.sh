#!/usr/bin/env bash
# RS19: crea el almacén de registros de seguridad fuera del perfil dev.
# El administrador (auditoría) queda sólo en manos de quien ejecuta este script; la aplicación recibe
# únicamente SECURITY_LOG_WRITER_PASSWORD, que sólo permite agregar eventos.
# Uso:
#   ./scripts/crear-registro-seguridad.sh [usuario-administrador]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/xperience.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Primero compilá el backend: cd backend && ./mvnw package" >&2
  exit 1
fi
read_secret() {
  printf '%s: ' "$1"
  stty -echo
  IFS= read -r REPLY
  stty echo
  printf '\n'
}
read_secret 'Contraseña del administrador del almacén (auditoría)'
SECURITY_LOG_ADMIN_PASSWORD="$REPLY"
read_secret 'Contraseña del escritor (la que recibirá la aplicación)'
SECURITY_LOG_WRITER_PASSWORD="$REPLY"
unset REPLY
export SECURITY_LOG_ADMIN_USER="${1:-registro_auditor}"
export SECURITY_LOG_ADMIN_PASSWORD SECURITY_LOG_WRITER_PASSWORD
cd "$ROOT/backend"
java -jar "$JAR" --spring.profiles.active=provision-registro --spring.main.web-application-type=none
unset SECURITY_LOG_ADMIN_PASSWORD SECURITY_LOG_WRITER_PASSWORD
echo "Iniciá el backend con SECURITY_LOG_WRITER_PASSWORD (y nunca con la contraseña del administrador)."
