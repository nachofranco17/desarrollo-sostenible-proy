export interface ErrorCampo {
  campo: string
  mensaje: string
}

/** Error devuelto por el backend. El código es estable; el mensaje es para mostrar. */
export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    readonly codigo: string,
    mensaje: string,
    readonly campos: ErrorCampo[] = [],
  ) {
    super(mensaje)
  }
}

const MENSAJES_POR_ESTADO: Record<number, string> = {
  401: 'Necesitás iniciar sesión.',
  403: 'Tu rol no permite realizar esta acción.',
  413: 'El archivo es demasiado grande.',
}

function leerCookie(nombre: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${nombre}=`))
    ?.slice(nombre.length + 1)
}

/**
 * Todas las llamadas al backend pasan por acá: misma origen, cookie de sesión y cabecera CSRF en
 * cada operación que modifica datos.
 */
export async function llamar<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const metodo = (opciones.method ?? 'GET').toUpperCase()
  const cabeceras = new Headers(opciones.headers)
  if (opciones.body && !(opciones.body instanceof FormData)) {
    cabeceras.set('Content-Type', 'application/json')
  }
  if (metodo !== 'GET' && metodo !== 'HEAD') {
    const token = leerCookie('XSRF-TOKEN')
    if (token) cabeceras.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  const respuesta = await fetch(ruta, { ...opciones, method: metodo, headers: cabeceras, credentials: 'same-origin' })

  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  const cuerpo = texto ? JSON.parse(texto) : undefined

  if (!respuesta.ok) {
    throw new ErrorApi(
      respuesta.status,
      cuerpo?.codigo ?? 'ERROR',
      cuerpo?.mensaje ?? MENSAJES_POR_ESTADO[respuesta.status] ?? 'Ocurrió un error inesperado.',
      cuerpo?.campos ?? [],
    )
  }
  return cuerpo as T
}

export const json = (datos: unknown) => JSON.stringify(datos)
