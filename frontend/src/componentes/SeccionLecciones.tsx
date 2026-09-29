import { useState, type FormEvent } from 'react'
import { gestion } from '../api/gestion'
import type { DetalleContenido, EscribirLeccion, Leccion } from '../api/tipos'
import { BotonConfirmar } from './comunes'

interface Props {
  contenido: DetalleContenido
  editable: boolean
  ocupado: boolean
  aplicar: (operacion: () => Promise<DetalleContenido>) => Promise<DetalleContenido | undefined>
}

export default function SeccionLecciones({ contenido, editable, ocupado, aplicar }: Props) {
  const [editando, setEditando] = useState<string | null>(null)
  const eliminable = editable && contenido.estado === 'BORRADOR'

  return (
    <div className="tarjeta">
      <h2>Lecciones <small className="sutil">({contenido.lecciones.length})</small></h2>
      <p className="sutil">El Talento puede recorrerlas en cualquier orden y marcarlas como completadas.</p>

      {contenido.lecciones.length === 0 && <p className="vacio">Agregá al menos una lección para poder publicar el curso.</p>}

      <ol className="elementos">
        {contenido.lecciones.map((l) => (
          <li key={l.id} className="elemento">
            {editando === l.id ? (
              <FormLeccion
                inicial={l}
                ocupado={ocupado}
                textoBoton="Guardar"
                onCancelar={() => setEditando(null)}
                onGuardar={async (datos) => {
                  if (await aplicar(() => gestion.actualizarLeccion(contenido.id, l.id, datos))) setEditando(null)
                }}
              />
            ) : (
              <VistaLeccion leccion={l}>
                {editable && <button className="boton-texto" onClick={() => setEditando(l.id)}>Editar</button>}
                {eliminable && (
                  <BotonConfirmar clase="boton-texto peligro" pregunta="¿Eliminar la lección?"
                    onConfirmar={() => void aplicar(() => gestion.eliminarLeccion(contenido.id, l.id))}>
                    Eliminar
                  </BotonConfirmar>
                )}
              </VistaLeccion>
            )}
          </li>
        ))}
      </ol>

      {editable && (
        <details className="agregar">
          <summary>Agregar lección</summary>
          <FormLeccion
            ocupado={ocupado}
            textoBoton="Agregar lección"
            limpiarAlGuardar
            onGuardar={(datos) => aplicar(() => gestion.agregarLeccion(contenido.id, datos))}
          />
        </details>
      )}
    </div>
  )
}

function VistaLeccion({ leccion, children }: { leccion: Leccion; children: React.ReactNode }) {
  return (
    <div>
      <div className="elemento-cabecera">
        <strong>{leccion.numero}. {leccion.titulo}</strong>
        <span className="acciones">{children}</span>
      </div>
      <p className="cuerpo">{leccion.cuerpo}</p>
    </div>
  )
}

function FormLeccion({ inicial, ocupado, textoBoton, onGuardar, onCancelar, limpiarAlGuardar }: {
  inicial?: EscribirLeccion
  ocupado: boolean
  textoBoton: string
  onGuardar: (datos: EscribirLeccion) => Promise<unknown>
  onCancelar?: () => void
  limpiarAlGuardar?: boolean
}) {
  const [titulo, setTitulo] = useState(inicial?.titulo ?? '')
  const [cuerpo, setCuerpo] = useState(inicial?.cuerpo ?? '')

  async function enviar(e: FormEvent) {
    e.preventDefault()
    const ok = await onGuardar({ titulo, cuerpo })
    if (ok && limpiarAlGuardar) {
      setTitulo('')
      setCuerpo('')
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <fieldset disabled={ocupado}>
        <label>Título<input required maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} /></label>
        <label>Contenido<textarea required maxLength={20000} rows={6} value={cuerpo} onChange={(e) => setCuerpo(e.target.value)} /></label>
        <div className="acciones">
          <button className="boton">{textoBoton}</button>
          {onCancelar && <button type="button" className="boton-secundario" onClick={onCancelar}>Cancelar</button>}
        </div>
      </fieldset>
    </form>
  )
}
