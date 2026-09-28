# Restricciones de acceso a nivel de campo

Requerimiento de seguridad: **R2** (ASVS v5.0.0-8.1.2)
Versión: 1.1 (27/09/2026)

Este documento extiende la [matriz de control de acceso](matriz-control-acceso.md) al nivel de campo, para las entidades que combinan datos de distinta sensibilidad. Define qué campos puede leer y cuáles puede modificar cada consumidor. Su implementación corresponde a R12.

Notación: **L** = lee, **E** = escribe, **—** = sin acceso. Los alcances (propio, org, postul., inscripto) son los definidos en la matriz de control de acceso.

## Reglas generales

Se aplican a todas las entidades, además de las tablas de cada una.

- **Campos de sistema.** Ningún consumidor los escribe desde el cliente: identificadores, `empresa_id`, usuario dueño del recurso, tipo de cuenta, fechas y campos calculados. El servidor los toma de la sesión (R11, R15) o los calcula.
- **Rechazo completo.** Una solicitud de escritura que incluya un campo que el consumidor no está autorizado a modificar se rechaza entera, sin aplicar ninguno de los cambios, y el intento se registra (R12, R18).
- **Secretos.** Nunca se incluyen en una respuesta: el hash de la contraseña, el token de invitación (que se almacena hasheado) y las respuestas aceptadas de los entregables.
- **"E al crear".** El campo se fija al crear el recurso y después no puede modificarse.

## Perfil del Talento

| Campo | Talento (propio) | Admin / Reclutador (postul.) | Admin / Editor (inscriptos) |
|---|---|---|---|
| Nombre y apellido | L/E | L | L |
| Correo | L/E | L | — |
| Teléfono | L/E | L, solo si la postulación está en Preseleccionada o Seleccionada | — |
| Especializaciones | L/E | L | — |
| Progreso, proyectos completados y logros | L (calculados) | L | — |
| Otras postulaciones | L | — | — |

La visibilidad del teléfono se evalúa sobre el estado de la postulación a través de la cual la empresa accede al perfil.

## Inscripción

| Campo | Talento (propia) | Admin / Editor (org) |
|---|---|---|
| Curso o proyecto | L, E al crear | L |
| Lecciones completadas | L/E | — |
| Entregables aprobados | L (los registra el backend) | — |
| Porcentaje de avance y estado | L (calculados) | L |

- El porcentaje de avance lo calcula el backend a partir de las lecciones completadas y los entregables aprobados. El cliente nunca lo envía, para que un Talento no pueda asignarse un avance que no tiene y obtener logros o certificados indebidamente.
- Para marcar una lección, el Talento solo indica cuál. La inscripción no viaja en la solicitud: el backend la obtiene a partir del usuario de la sesión y del curso al que pertenece la lección. Si no existe, la operación se deniega (R11, R13).
- Un entregable aprobado solo lo registra el backend, cuando la respuesta enviada coincide con una respuesta aceptada.

## Entregable

| Campo | Talento (inscripto) | Admin / Editor (org) |
|---|---|---|
| Número y enunciado | L | L/E |
| Pista | L, solo en la respuesta a un envío incorrecto | L/E |
| Respuestas aceptadas | — | E (se reemplazan, nunca se leen) |

### Verificación de respuestas

- **Normalización.** Antes de hashear se aplica la misma función de normalización tanto a las respuestas aceptadas, cuando la empresa las carga, como a la respuesta del Talento, cuando la envía: recorte de espacios en los extremos, colapso de espacios repetidos, minúsculas y saltos de línea unificados.
- **Varias respuestas aceptadas.** Cada entregable puede tener más de una, para cubrir formas equivalentes de escribir el mismo resultado. Cada una se almacena hasheada con el `PasswordEncoder` de Spring Security, igual que las contraseñas.
- **Resultado.** Si la respuesta coincide con alguna aceptada, se registra el entregable como aprobado. Si no coincide, se devuelve la pista cargada por la empresa.
- **Límite de envíos.** Cada usuario puede enviar hasta 20 respuestas por minuto. Superado el límite, la solicitud se rechaza con "demasiadas solicitudes" hasta que se recargue. Se implementa en memoria con Bucket4j.
- **Intentos.** No se almacenan, solo el aprobado.

## Postulación

| Campo | Talento (propia) | Admin / Reclutador (org) |
|---|---|---|
| Oferta | L, E al crear | L |
| Currículum adjunto | L, E al crear | L |
| Fecha | L | L |
| Estado | L | L/E, según las transiciones permitidas |

El currículum adjunto es el que el Talento envió al postularse. Si después actualiza el de su perfil, la postulación conserva el original.

### Estados y transiciones

| Desde | Hacia |
|---|---|
| Enviada | Preseleccionada, Descartada |
| Preseleccionada | Seleccionada, Descartada |

- Toda postulación nace en **Enviada**.
- **Seleccionada** y **Descartada** son estados finales y no pueden modificarse.
- Una postulación no puede pasar directamente de Enviada a Seleccionada. Así, el Talento siempre ve el cambio a Preseleccionada, que le avisa que la empresa ya accede a su teléfono, antes de ser contactado.
- Cualquier transición no listada se rechaza.

## Membresía

| Campo | Miembro (propia) | Admin (org) |
|---|---|---|
| Empresa y usuario | L | L |
| Rol | L | L/E (+R23), salvo sobre la propia membresía |
| Estado (pendiente / activa / baja) | L | L/E (+R23), salvo sobre la propia membresía |
| Token de invitación | — | — |

El Admin no puede modificar el rol ni el estado de su propia membresía. Como las empresas las da de alta el equipo de desarrollo, esta restricción evita que una empresa quede sin nadie que la administre.

## Decisiones de diseño

1. **No se almacena el documento de identidad.** Ninguna funcionalidad de la plataforma lo necesita, y guardarlo sin una finalidad concreta va contra el principio de minimización de datos. Si en el futuro aparece un uso real, se incorpora junto con esa finalidad.
2. **El contacto con el postulante es escalonado.** La empresa ve el correo desde que recibe la postulación y el teléfono recién desde Preseleccionada. Se evaluó una mensajería interna, pero se descartó por su costo de implementación y porque agrega un recurso nuevo que proteger. El escalonamiento cumple el mismo objetivo reutilizando el estado de la postulación, que actúa como atributo de la decisión de autorización (R4).
3. **La empresa ve de sus inscriptos solo el nombre y el avance.** El resto del perfil queda reservado a las empresas a las que el Talento se postuló.
4. **Los entregables se validan con respuesta verificable.** Se descartaron la ejecución de código del Talento en el servidor, por el riesgo de ejecutar código de terceros, y la validación con IA, por el riesgo de inyección de prompt, el envío de datos personales a un tercero y el alcance del proyecto.
5. **El feedback es una pista, no una medida de cercanía.** La pista orienta al Talento sin revelar la respuesta.
6. **Riesgo aceptado: respuestas compartidas.** Todos los Talentos tienen las mismas respuestas aceptadas, por lo que pueden pasárselas entre ellos.
