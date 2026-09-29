import { useState, type FormEvent } from 'react'
import { gestion } from '../api/gestion'
import type { DetalleContenido, Entregable, EscribirEntregable } from '../api/tipos'
import { BotonConfirmar } from './comunes'

interface Props {
  contenido: DetalleContenido
  editable: boolean
  ocupado: boolean
  aplicar: (operacion: () => Promise<DetalleContenido>) => Promise<DetalleContenido | undefined>
}

export default function SeccionEntregables({ contenido, editable, ocupado, aplicar }: Props) {
  const [editando, setEditando] = useState<string | null>(null)
  const eliminable = editable && contenido.estado === 'BORRADOR'

  return (
    <div className="tarjeta">
      <h2>Entregables <small className="sutil">({contenido.entregables.length})</small></h2>
      <p className="sutil">
        El Talento envía su respuesta y se compara contra las respuestas aceptadas, sin distinguir mayúsculas ni espacios.
        Si no coincide, ve la pista. Las respuestas aceptadas se guardan como hash: nadie puede volver a leerlas, sólo reemplazarlas.
      </p>

      {contenido.entregables.length === 0 && <p className="vacio">Agregá al menos un entregable para poder publicar el proyecto.</p>}

      <ol className="elementos">
        {contenido.entregables.map((e) => (
          <li key={e.id} className="elemento">
            {editando === e.id ? (
              <FormEntregable
                inicial={e}
                ocupado={ocupado}
                textoBoton="Guardar"
                onCancelar={() => setEditando(null)}
                onGuardar={async (datos) => {
                  if (await aplicar(() => gestion.actualizarEntregable(contenido.id, e.id, datos))) setEditando(null)
                }}
              />
            ) : (
              <VistaEntregable entregable={e}>
                {editable && <button className="boton-texto" onClick={() => setEditando(e.id)}>Editar</button>}
                {eliminable && (
                  <BotonConfirmar clase="boton-texto peligro" pregunta="¿Eliminar el entregable?"
                    onConfirmar={() => void aplicar(() => gestion.eliminarEntregable(contenido.id, e.id))}>
                    Eliminar
                  </BotonConfirmar>
                )}
              </VistaEntregable>
            )}
          </li>
        ))}
      </ol>

      {editable && (
        <details className="agregar">
          <summary>Agregar entregable</summary>
          <FormEntregable
            ocupado={ocupado}
            textoBoton="Agregar entregable"
            limpiarAlGuardar
            onGuardar={(datos) => aplicar(() => gestion.agregarEntregable(contenido.id, datos))}
          />
        </details>
      )}
    </div>
  )
}

function VistaEntregable({ entregable, children }: { entregable: Entregable; children: React.ReactNode }) {
  return (
    <div>
      <div className="elemento-cabecera">
        <strong>{entregable.numero}. {entregable.titulo}</strong>
        <span className="acciones">{children}</span>
      </div>
      <p className="cuerpo">{entregable.consigna}</p>
      <p className="sutil">
        Pista: {entregable.pista} · {entregable.cantidadRespuestasAceptadas}{' '}
        {entregable.cantidadRespuestasAceptadas === 1 ? 'respuesta aceptada' : 'respuestas aceptadas'}
      </p>
    </div>
  )
}

function FormEntregable({ inicial, ocupado, textoBoton, onGuardar, onCancelar, limpiarAlGuardar }: {
  inicial?: Entregable
  ocupado: boolean
  textoBoton: string
  onGuardar: (datos: EscribirEntregable) => Promise<unknown>
  onCancelar?: () => void
  limpiarAlGuardar?: boolean
}) {
  const [titulo, setTitulo] = useState(inicial?.titulo ?? '')
  const [consigna, setConsigna] = useState(inicial?.consigna ?? '')
  const [pista, setPista] = useState(inicial?.pista ?? '')
  const [respuestas, setRespuestas] = useState('')
  // Al editar, las respuestas se conservan salvo que se pida reemplazarlas explícitamente.
  const [reemplazar, setReemplazar] = useState(!inicial)

  async function enviar(e: FormEvent) {
    e.preventDefault()
    const datos: EscribirEntregable = { titulo, consigna, pista }
    if (reemplazar) {
      datos.respuestasAceptadas = respuestas.split('\n').map((r) => r.trim()).filter(Boolean)
    }
    const ok = await onGuardar(datos)
    if (ok && limpiarAlGuardar) {
      setTitulo('')
      setConsigna('')
      setPista('')
      setRespuestas('')
    }
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <fieldset disabled={ocupado}>
        <label>Título<input required maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} /></label>
        <label>Consigna<textarea required maxLength={4000} rows={4} value={consigna} onChange={(e) => setConsigna(e.target.value)} /></label>
        <label>Pista si la respuesta no coincide<input required maxLength={500} value={pista} onChange={(e) => setPista(e.target.value)} /></label>
        {inicial && (
          <label className="casilla">
            <input type="checkbox" checked={reemplazar} onChange={(e) => setReemplazar(e.target.checked)} />
            Reemplazar las {inicial.cantidadRespuestasAceptadas} respuestas aceptadas actuales
          </label>
        )}
        {reemplazar && (
          <label>
            Respuestas aceptadas (una por línea, hasta 20)
            <textarea required rows={3} value={respuestas} onChange={(e) => setRespuestas(e.target.value)}
              autoComplete="off" spellCheck={false} />
          </label>
        )}
        <div className="acciones">
          <button className="boton">{textoBoton}</button>
          {onCancelar && <button type="button" className="boton-secundario" onClick={onCancelar}>Cancelar</button>}
        </div>
      </fieldset>
    </form>
  )
}
