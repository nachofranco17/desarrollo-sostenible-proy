import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { gestion } from '../../api/gestion'
import { TIPOS, type DetalleContenido } from '../../api/tipos'
import FormularioContenido from '../../componentes/FormularioContenido'
import SeccionEntregables from '../../componentes/SeccionEntregables'
import SeccionLecciones from '../../componentes/SeccionLecciones'
import SeccionMateriales from '../../componentes/SeccionMateriales'
import { BotonConfirmar, EstadoBadge, MensajeError } from '../../componentes/comunes'
import { formatearFecha, useOperacion } from '../../componentes/operacion'

export default function EditorContenido() {
  const { id = '' } = useParams()
  const navegar = useNavigate()
  const { error, ocupado, ejecutar } = useOperacion()
  const [contenido, setContenido] = useState<DetalleContenido | null>(null)
  const [guardado, setGuardado] = useState(false)

  useEffect(() => {
    void ejecutar(() => gestion.detalle(id)).then((d) => d && setContenido(d))
  }, [id, ejecutar])

  /** Toda operación devuelve el detalle actualizado: se reemplaza el estado completo. */
  async function aplicar(operacion: () => Promise<DetalleContenido>) {
    const actualizado = await ejecutar(operacion)
    if (actualizado) setContenido(actualizado)
    return actualizado
  }

  if (!contenido) {
    return (
      <section>
        <p><Link to="/gestion">← Volver</Link></p>
        {error ? <MensajeError error={error} /> : <p className="cargando">Cargando…</p>}
      </section>
    )
  }

  const bajado = contenido.estado === 'BAJADO'
  const editable = !bajado

  return (
    <section>
      <p><Link to="/gestion">← Volver</Link></p>
      <div className="titulo-pagina">
        <div>
          <p className="sutil">{TIPOS[contenido.tipo]} · actualizado {formatearFecha(contenido.actualizadoEn)}</p>
          <h1>{contenido.titulo} <EstadoBadge estado={contenido.estado} /></h1>
        </div>
        <div className="acciones">
          {contenido.estado !== 'PUBLICADO' && (
            <button className="boton" disabled={ocupado} onClick={() => void aplicar(() => gestion.publicar(id))}>
              {bajado ? 'Volver a publicar' : 'Publicar'}
            </button>
          )}
          {contenido.estado === 'PUBLICADO' && (
            <BotonConfirmar clase="boton-secundario" deshabilitado={ocupado}
              pregunta="Deja de admitir inscripciones; los inscriptos conservan el acceso. ¿Confirmás?"
              onConfirmar={() => void aplicar(() => gestion.bajar(id))}>
              Dar de baja
            </BotonConfirmar>
          )}
          {contenido.estado === 'BORRADOR' && (
            <BotonConfirmar deshabilitado={ocupado} pregunta="¿Eliminar definitivamente este borrador?"
              onConfirmar={async () => {
                const ok = await ejecutar(async () => { await gestion.eliminar(id); return true })
                if (ok) navegar('/gestion', { replace: true })
              }}>
              Eliminar
            </BotonConfirmar>
          )}
        </div>
      </div>

      <MensajeError error={error} />
      {bajado && <p className="nota">Este contenido está dado de baja: no admite nuevas inscripciones ni modificaciones.</p>}
      {contenido.estado === 'PUBLICADO' && (
        <p className="nota">Está publicado: podés editar y agregar elementos, pero no eliminar lecciones ni entregables porque puede haber Talentos avanzando en ellos.</p>
      )}

      <div className="tarjeta">
        <h2>Datos generales</h2>
        <FormularioContenido
          key={contenido.actualizadoEn}
          inicial={contenido}
          deshabilitado={!editable}
          ocupado={ocupado}
          textoBoton={guardado ? 'Guardado ✓' : 'Guardar cambios'}
          onGuardar={async ({ titulo, descripcion, tecnologia, nivel, duracionHoras, costo }) => {
            // El tipo se elige al crear y no se modifica.
            const datos = { titulo, descripcion, tecnologia, nivel, duracionHoras, costo }
            if (await aplicar(() => gestion.actualizar(id, datos))) {
              setGuardado(true)
              setTimeout(() => setGuardado(false), 2000)
            }
          }}
        />
      </div>

      {contenido.tipo === 'CURSO' ? (
        <SeccionLecciones contenido={contenido} editable={editable} ocupado={ocupado} aplicar={aplicar} />
      ) : (
        <SeccionEntregables contenido={contenido} editable={editable} ocupado={ocupado} aplicar={aplicar} />
      )}

      <SeccionMateriales contenido={contenido} ocupado={ocupado} aplicar={aplicar} />
    </section>
  )
}
