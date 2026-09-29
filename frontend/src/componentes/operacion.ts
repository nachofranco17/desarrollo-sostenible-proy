import { useCallback, useState } from 'react'
import { ErrorApi } from '../api/cliente'

/** Ejecuta una operación contra el backend y deja el error listo para mostrar. */
export function useOperacion() {
  const [error, setError] = useState<ErrorApi | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const ejecutar = useCallback(
    async <T,>(operacion: () => Promise<T>): Promise<T | undefined> => {
      setOcupado(true)
      setError(null)
      try {
        return await operacion()
      } catch (e) {
        const err = e instanceof ErrorApi ? e : new ErrorApi(0, 'RED', 'No se pudo conectar con el servidor.')
        setError(err)
        return undefined
      } finally {
        setOcupado(false)
      }
    },
    [],
  )

  return { error, ocupado, ejecutar, limpiarError: () => setError(null) }
}

export function formatearFecha(iso: string): string {
  return new Date(iso).toLocaleString('es-UY', { dateStyle: 'short', timeStyle: 'short' })
}

export function formatearTamanio(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
