import { useRef } from 'react'
import { gestion } from '../api/gestion'
import type { DetalleContenido } from '../api/tipos'
import { BotonConfirmar } from './comunes'
import { formatearFecha, formatearTamanio } from './operacion'

interface Props {
  contenido: DetalleContenido
  ocupado: boolean
  aplicar: (operacion: () => Promise<DetalleContenido>) => Promise<DetalleContenido | undefined>
}

export default function SeccionMateriales({ contenido, ocupado, aplicar }: Props) {
  const entrada = useRef<HTMLInputElement>(null)
  const modificable = contenido.estado !== 'BAJADO'

  async function subir(archivo: File | undefined) {
    if (!archivo) return
    await aplicar(() => gestion.subirMaterial(contenido.id, archivo))
    if (entrada.current) entrada.current.value = ''
  }

  return (
    <div className="tarjeta">
      <h2>Material <small className="sutil">({contenido.materiales.length})</small></h2>
      <p className="sutil">PDF, PNG, JPG, ZIP o MP4, hasta 25 MB. Sólo se descarga a través de la plataforma.</p>

      {contenido.materiales.length === 0 && <p className="vacio">Sin material cargado.</p>}

      <ul className="materiales">
        {contenido.materiales.map((m) => (
          <li key={m.id}>
            <a href={gestion.urlMaterial(contenido.id, m.id)}>{m.nombre}</a>
            <span className="sutil">{formatearTamanio(m.tamanioBytes)} · {formatearFecha(m.subidoEn)}</span>
            {modificable && (
              <BotonConfirmar clase="boton-texto peligro" pregunta="¿Eliminar el archivo?" deshabilitado={ocupado}
                onConfirmar={() => void aplicar(() => gestion.eliminarMaterial(contenido.id, m.id))}>
                Eliminar
              </BotonConfirmar>
            )}
          </li>
        ))}
      </ul>

      {modificable && (
        <label className="subida">
          <input ref={entrada} type="file" disabled={ocupado}
            accept=".pdf,.png,.jpg,.jpeg,.zip,.mp4,application/pdf,image/png,image/jpeg,application/zip,video/mp4"
            onChange={(e) => void subir(e.target.files?.[0])} />
        </label>
      )}
    </div>
  )
}
