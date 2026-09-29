import { useState, type ReactNode } from 'react'
import { ErrorApi } from '../api/cliente'
import { ESTADOS, type EstadoContenido } from '../api/tipos'

export function EstadoBadge({ estado }: { estado: EstadoContenido }) {
  return <span className={`badge badge-${estado.toLowerCase()}`}>{ESTADOS[estado]}</span>
}

export function MensajeError({ error }: { error: ErrorApi | null }) {
  if (!error) return null
  return (
    <div className="error" role="alert">
      {error.message}
      {error.campos.length > 0 && (
        <ul>
          {error.campos.map((c) => <li key={c.campo}><strong>{c.campo}</strong>: {c.mensaje}</li>)}
        </ul>
      )}
    </div>
  )
}

/** Botón que pide confirmación en la misma línea antes de ejecutar una acción destructiva. */
export function BotonConfirmar({
  children,
  pregunta,
  onConfirmar,
  deshabilitado,
  clase = 'boton-peligro',
}: {
  children: ReactNode
  pregunta: string
  onConfirmar: () => void
  deshabilitado?: boolean
  clase?: string
}) {
  const [confirmando, setConfirmando] = useState(false)
  if (!confirmando) {
    return <button type="button" className={clase} disabled={deshabilitado} onClick={() => setConfirmando(true)}>{children}</button>
  }
  return (
    <span className="confirmacion">
      {pregunta}
      <button type="button" className="boton-peligro" onClick={() => { setConfirmando(false); onConfirmar() }}>Sí</button>
      <button type="button" className="boton-secundario" onClick={() => setConfirmando(false)}>No</button>
    </span>
  )
}
