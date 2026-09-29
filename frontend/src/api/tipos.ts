export type TipoContenido = 'CURSO' | 'PROYECTO'
export type Nivel = 'INICIAL' | 'INTERMEDIO' | 'AVANZADO'
export type EstadoContenido = 'BORRADOR' | 'PUBLICADO' | 'BAJADO'

export interface DatosContenido {
  titulo: string
  descripcion: string
  tecnologia: string
  nivel: Nivel
  duracionHoras: number
  costo: number
}

export interface CrearContenido extends DatosContenido {
  tipo: TipoContenido
}

export interface ResumenContenido {
  id: string
  tipo: TipoContenido
  titulo: string
  tecnologia: string
  nivel: Nivel
  estado: EstadoContenido
  cantidadElementos: number
  actualizadoEn: string
}

export interface Leccion {
  id: string
  numero: number
  titulo: string
  cuerpo: string
}

export interface Entregable {
  id: string
  numero: number
  titulo: string
  consigna: string
  pista: string
  cantidadRespuestasAceptadas: number
}

export interface Material {
  id: string
  nombre: string
  tipoMime: string
  tamanioBytes: number
  subidoEn: string
}

export interface DetalleContenido extends DatosContenido {
  id: string
  tipo: TipoContenido
  estado: EstadoContenido
  creadoEn: string
  actualizadoEn: string
  lecciones: Leccion[]
  entregables: Entregable[]
  materiales: Material[]
}

export interface EscribirLeccion {
  titulo: string
  cuerpo: string
}

export interface EscribirEntregable {
  titulo: string
  consigna: string
  pista: string
  /** Al editar es opcional: si se omite, se conservan las respuestas actuales. */
  respuestasAceptadas?: string[]
}

export const NIVELES: Record<Nivel, string> = {
  INICIAL: 'Inicial',
  INTERMEDIO: 'Intermedio',
  AVANZADO: 'Avanzado',
}

export const ESTADOS: Record<EstadoContenido, string> = {
  BORRADOR: 'Borrador',
  PUBLICADO: 'Publicado',
  BAJADO: 'Dado de baja',
}

export const TIPOS: Record<TipoContenido, string> = {
  CURSO: 'Curso',
  PROYECTO: 'Proyecto',
}
