import { Link, useNavigate } from 'react-router-dom'
import { gestion } from '../../api/gestion'
import FormularioContenido from '../../componentes/FormularioContenido'
import { MensajeError } from '../../componentes/comunes'
import { useOperacion } from '../../componentes/operacion'

export default function NuevoContenido() {
  const navegar = useNavigate()
  const { error, ocupado, ejecutar } = useOperacion()

  return (
    <section>
      <p><Link to="/gestion">← Volver</Link></p>
      <h1>Nuevo curso o proyecto</h1>
      <p className="sutil">Se crea como borrador. Vas a poder agregar lecciones, entregables y material antes de publicarlo.</p>
      <MensajeError error={error} />
      <div className="tarjeta">
        <FormularioContenido
          conTipo
          ocupado={ocupado}
          textoBoton="Crear borrador"
          onGuardar={async (datos) => {
            const creado = await ejecutar(() => gestion.crear(datos))
            if (creado) navegar(`/gestion/${creado.id}`, { replace: true })
          }}
        />
      </div>
    </section>
  )
}
