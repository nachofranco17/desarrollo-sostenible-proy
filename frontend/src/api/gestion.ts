import { json, llamar } from './cliente'
import type {
  CrearContenido,
  DatosContenido,
  DetalleContenido,
  EscribirEntregable,
  EscribirLeccion,
  ResumenContenido,
} from './tipos'

const BASE = '/api/gestion/contenidos'

export const gestion = {
  listar: () => llamar<ResumenContenido[]>(BASE),
  detalle: (id: string) => llamar<DetalleContenido>(`${BASE}/${id}`),
  crear: (datos: CrearContenido) => llamar<DetalleContenido>(BASE, { method: 'POST', body: json(datos) }),
  actualizar: (id: string, datos: DatosContenido) =>
    llamar<DetalleContenido>(`${BASE}/${id}`, { method: 'PUT', body: json(datos) }),
  eliminar: (id: string) => llamar<void>(`${BASE}/${id}`, { method: 'DELETE' }),
  publicar: (id: string) => llamar<DetalleContenido>(`${BASE}/${id}/publicar`, { method: 'POST' }),
  bajar: (id: string) => llamar<DetalleContenido>(`${BASE}/${id}/bajar`, { method: 'POST' }),

  agregarLeccion: (id: string, datos: EscribirLeccion) =>
    llamar<DetalleContenido>(`${BASE}/${id}/lecciones`, { method: 'POST', body: json(datos) }),
  actualizarLeccion: (id: string, leccionId: string, datos: EscribirLeccion) =>
    llamar<DetalleContenido>(`${BASE}/${id}/lecciones/${leccionId}`, { method: 'PUT', body: json(datos) }),
  eliminarLeccion: (id: string, leccionId: string) =>
    llamar<DetalleContenido>(`${BASE}/${id}/lecciones/${leccionId}`, { method: 'DELETE' }),

  agregarEntregable: (id: string, datos: EscribirEntregable) =>
    llamar<DetalleContenido>(`${BASE}/${id}/entregables`, { method: 'POST', body: json(datos) }),
  actualizarEntregable: (id: string, entregableId: string, datos: EscribirEntregable) =>
    llamar<DetalleContenido>(`${BASE}/${id}/entregables/${entregableId}`, { method: 'PUT', body: json(datos) }),
  eliminarEntregable: (id: string, entregableId: string) =>
    llamar<DetalleContenido>(`${BASE}/${id}/entregables/${entregableId}`, { method: 'DELETE' }),

  subirMaterial: (id: string, archivo: File) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return llamar<DetalleContenido>(`${BASE}/${id}/materiales`, { method: 'POST', body: formulario })
  },
  urlMaterial: (id: string, materialId: string) => `${BASE}/${id}/materiales/${materialId}`,
  eliminarMaterial: (id: string, materialId: string) =>
    llamar<DetalleContenido>(`${BASE}/${id}/materiales/${materialId}`, { method: 'DELETE' }),
}
