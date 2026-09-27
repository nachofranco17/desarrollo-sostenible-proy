# Matriz de control de acceso

Requerimiento de seguridad: **RS1** (ASVS v5.0.0-8.1.1)
Versión: 1.0 (27/09/2026)

Este documento define, para cada tipo de consumidor, qué operaciones puede invocar y sobre qué conjunto de datos. Toda decisión de autorización de la plataforma se contrasta contra esta matriz. Es además la base para cargar los permisos del modelo ABAC (R4), para los conjuntos de operaciones por rol (R9) y para los casos de prueba de autorización (R20).

Las restricciones a nivel de campo (qué campos puede leer o modificar cada consumidor) se definen por separado en R2.

## Consumidores

| Consumidor | Cómo se crea la cuenta |
|---|---|
| **Visitante** | No autenticado. |
| **Talento** | Registro desde la aplicación. Recibe su conjunto base de permisos al registrarse. |
| **Admin de empresa** | Lo da de alta el equipo de desarrollo junto con la empresa, fuera de la aplicación. |
| **Reclutador** | Invitado por el Admin de su empresa. Queda sin rol hasta que el Admin se lo asigna (R7). |
| **Editor de contenido** | Invitado por el Admin de su empresa. Queda sin rol hasta que el Admin se lo asigna (R7). |

## Matriz

| Recurso | Operación | Visitante | Talento | Admin Empresa | Reclutador | Editor |
|---|---|---|---|---|---|---|
| Cuenta | Registrarse como Talento | ✓ | — | — | — | — |
| | Ver y modificar configuración | — | propia | propia | propia | propia |
| Perfil del Talento | Ver y modificar | — | propio | — | — | — |
| | Ver perfil de postulante (campos de R2) | — | — | postul. | postul. | — |
| Catálogo | Buscar y ver cursos/proyectos publicados | ✓ | ✓ | ✓ | ✓ | ✓ |
| Curso/proyecto | Crear, modificar, cambiar estado, eliminar | — | — | org | — | org |
| | Ver borradores | — | — | org | — | org |
| Material | Subir o reemplazar | — | — | org | — | org |
| | Descargar | — | inscripto | org | — | org |
| Inscripción | Inscribirse a publicados | — | ✓ | — | — | — |
| | Ver y actualizar avance | — | propia | — | — | — |
| | Ver inscriptos y su avance (campos de R2) | — | — | org | — | org |
| Oferta | Ver publicadas | — | ✓ | ✓ | ✓ | ✓ |
| | Crear, modificar, cerrar, ver borradores | — | — | org | org | — |
| Postulación | Postularse a ofertas publicadas | — | ✓ | — | — | — |
| | Ver propias y su estado | — | propias | — | — | — |
| | Ver listado y cambiar estado | — | — | org | org | — |
| Currículum | Subir o reemplazar | — | propio | — | — | — |
| | Descargar | — | propio | postul. | postul. | — |
| Staff | Invitar, asignar/cambiar rol, dar de baja | — | — | org (+R23) | — | — |
| | Ver miembros y permisos efectivos (R21) | — | — | org | — | — |

### Alcances

| Símbolo | Significado |
|---|---|
| **✓** | Cualquier instancia que cumpla la condición de la operación (por ejemplo, estar publicada), sin importar a qué usuario o empresa pertenezca. |
| **propio / propia / propias** | Solo las instancias del propio usuario (R11). |
| **org** | Solo recursos de su empresa. El `empresa_id` se toma siempre de la sesión, nunca de la solicitud (R15). |
| **postul.** | Solo Talentos que se postularon a una oferta de su empresa. |
| **inscripto** | Solo material de cursos o proyectos a los que el Talento está inscripto. |
| **+R23** | Requiere reautenticación antes de ejecutar la operación. |
| **—** | Denegado. |

**Todo lo que no figura en esta matriz está denegado** (R6 y R8). Una funcionalidad nueva no es accesible hasta que se agregue aquí con sus permisos.

## Casos fuera de la matriz

- **Aceptación de una invitación al staff.** El sujeto no es un usuario con sesión sino el portador del token, enviado por correo. El endpoint igualmente atraviesa el PEP (R5). La membresía resultante queda sin rol hasta que el Admin de la empresa lo asigne (R7).
- **Registros de seguridad.** Ningún consumidor accede a ellos desde la aplicación, ni siquiera el Admin de empresa (R19).
- **Alta de empresas y de su Admin.** La realiza el equipo de desarrollo mediante un script (u otro medio) versionado en este repositorio, que deja registro de cada alta. No existe pantalla de registro de empresas ni un tipo de usuario Administrador de plataforma.

## Decisiones de diseño

1. **No existe el Administrador de plataforma.** Las tareas de alta de empresas las hace el equipo de desarrollo (nosotros) por fuera de la aplicación, con el script u opción alternativa mencionada arriba.
2. **El Talento recibe su conjunto base de permisos al registrarse.** Ese conjunto es su mínimo (R7). No se separa "buscar en el catálogo" de "inscribirse" o "postularse".
3. **Una cuenta es de Talento o de staff, nunca ambas.** Si una persona quiere las dos cosas, necesita dos cuentas.
4. **Cada miembro del staff pertenece a una sola empresa y tiene un solo rol.**
5. **El correo es único por cuenta.** Dos cuentas no pueden compartir correo. Cuando el correo ya existe, la respuesta al registro o a la invitación es genérica, para no revelar que esa persona usa la plataforma.
