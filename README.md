# XPerience

Plataforma de formación y empleabilidad para jóvenes talentos IT. Proyecto de *Seguridad en el
desarrollo de software* (UM, 2026).

| Carpeta     | Qué hay                                            |
|-------------|----------------------------------------------------|
| `backend/`  | Java 17, Spring Boot 3.3, JPA + Flyway, PostgreSQL |
| `frontend/` | React 18 + TypeScript + Vite                       |
| `docs/`     | Documentación de seguridad                         |

## Estado

- [x] RF5 — Gestión de cursos y proyectos por la empresa (ABM, lecciones, entregables, material, estados)
- [x] R9 — Mínimo privilegio por rol ([docs/seguridad/r9-permisos-por-rol.md](docs/seguridad/r9-permisos-por-rol.md))
- [ ] RF1 — Inicio de sesión. Hasta que exista, la API responde 401: debe dejar un `Consumidor`
      como principal de Spring Security (ver `seguridad/Consumidor.java`).

## Cómo levantarlo

Requisitos: JDK 17 y Node 20. Docker es opcional.

```bash
# Backend (puerto 8080), con base en memoria
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# o con PostgreSQL
docker compose up -d
cd backend
./mvnw spring-boot:run

# Frontend (puerto 5173, reenvía /api al backend)
cd frontend
npm install
npm run dev
```

Pruebas del backend:

```bash
cd backend && ./mvnw test
```
