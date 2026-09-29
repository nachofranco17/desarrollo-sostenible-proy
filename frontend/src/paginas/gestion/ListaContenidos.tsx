import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { gestion } from '../../api/gestion'
import { NIVELES, TIPOS, type ResumenContenido } from '../../api/tipos'
import { EstadoBadge, MensajeError } from '../../componentes/comunes'
import { formatearFecha, useOperacion } from '../../componentes/operacion'

export default function ListaContenidos() {
  const { error, ejecutar } = useOperacion()
  const [contenidos, setContenidos] = useState<ResumenContenido[] | null>(null)

  useEffect(() => {
    void ejecutar(gestion.listar).then((r) => r && setContenidos(r))
  }, [ejecutar])

  return (
    <section>
      <div className="titulo-pagina">
        <h1>Cursos y proyectos</h1>
        <Link className="boton" to="/gestion/nuevo">Nuevo curso o proyecto</Link>
      </div>

      <MensajeError error={error} />

      {contenidos?.length === 0 && (
        <div className="vacio">
          <p>Todavía no hay cursos ni proyectos.</p>
          <Link to="/gestion/nuevo">Crear el primero</Link>
        </div>
      )}

      {contenidos && contenidos.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Título</th>
                <th>Tipo</th>
                <th>Tecnología</th>
                <th>Nivel</th>
                <th>Elementos</th>
                <th>Estado</th>
                <th>Actualizado</th>
              </tr>
            </thead>
            <tbody>
              {contenidos.map((c) => (
                <tr key={c.id}>
                  <td><Link to={`/gestion/${c.id}`}>{c.titulo}</Link></td>
                  <td>{TIPOS[c.tipo]}</td>
                  <td>{c.tecnologia}</td>
                  <td>{NIVELES[c.nivel]}</td>
                  <td>{c.cantidadElementos} {c.tipo === 'CURSO' ? 'lecciones' : 'entregables'}</td>
                  <td><EstadoBadge estado={c.estado} /></td>
                  <td>{formatearFecha(c.actualizadoEn)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
