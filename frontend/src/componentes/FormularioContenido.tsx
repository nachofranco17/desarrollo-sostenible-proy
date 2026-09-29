import { useState, type FormEvent } from 'react'
import { NIVELES, TIPOS, type CrearContenido, type DatosContenido, type Nivel, type TipoContenido } from '../api/tipos'

const VACIO: CrearContenido = {
  tipo: 'CURSO',
  titulo: '',
  descripcion: '',
  tecnologia: '',
  nivel: 'INICIAL',
  duracionHoras: 10,
  costo: 0,
}

interface Props {
  inicial?: DatosContenido
  /** Al crear se elige el tipo; después ya no se puede cambiar. */
  conTipo?: boolean
  deshabilitado?: boolean
  ocupado?: boolean
  textoBoton: string
  onGuardar: (datos: CrearContenido) => void
}

/** Sólo los campos imprescindibles de un curso o proyecto (RF5). */
export default function FormularioContenido({ inicial, conTipo, deshabilitado, ocupado, textoBoton, onGuardar }: Props) {
  const [datos, setDatos] = useState<CrearContenido>({ ...VACIO, ...inicial })
  const cambiar = <K extends keyof CrearContenido>(campo: K, valor: CrearContenido[K]) =>
    setDatos((d) => ({ ...d, [campo]: valor }))

  function enviar(e: FormEvent) {
    e.preventDefault()
    onGuardar(datos)
  }

  return (
    <form className="formulario" onSubmit={enviar}>
      <fieldset disabled={deshabilitado || ocupado}>
        {conTipo && (
          <div className="opciones-tipo">
            {(Object.keys(TIPOS) as TipoContenido[]).map((t) => (
              <label key={t} className={datos.tipo === t ? 'opcion activa' : 'opcion'}>
                <input type="radio" name="tipo" checked={datos.tipo === t} onChange={() => cambiar('tipo', t)} />
                <strong>{TIPOS[t]}</strong>
                <small>{t === 'CURSO' ? 'Lecciones numeradas que el Talento recorre y marca como completadas.' : 'Entregables con respuesta verificable y una pista.'}</small>
              </label>
            ))}
          </div>
        )}
        <label>
          Título
          <input required maxLength={150} value={datos.titulo} onChange={(e) => cambiar('titulo', e.target.value)} />
        </label>
        <label>
          Descripción
          <textarea required maxLength={4000} rows={4} value={datos.descripcion}
            onChange={(e) => cambiar('descripcion', e.target.value)} />
        </label>
        <div className="fila">
          <label>
            Tecnología
            <input required maxLength={60} placeholder="Java, React, SQL…" value={datos.tecnologia}
              onChange={(e) => cambiar('tecnologia', e.target.value)} />
          </label>
          <label>
            Nivel
            <select value={datos.nivel} onChange={(e) => cambiar('nivel', e.target.value as Nivel)}>
              {(Object.keys(NIVELES) as Nivel[]).map((n) => <option key={n} value={n}>{NIVELES[n]}</option>)}
            </select>
          </label>
          <label>
            Duración (horas)
            <input type="number" required min={1} max={1000} value={datos.duracionHoras}
              onChange={(e) => cambiar('duracionHoras', Number(e.target.value))} />
          </label>
          <label>
            Costo (USD)
            <input type="number" required min={0} max={100000} step="0.01" value={datos.costo}
              onChange={(e) => cambiar('costo', Number(e.target.value))} />
          </label>
        </div>
        {!deshabilitado && <button className="boton" disabled={ocupado}>{textoBoton}</button>}
      </fieldset>
    </form>
  )
}
