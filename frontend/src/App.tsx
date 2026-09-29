import { Link, Navigate, Route, Routes } from 'react-router-dom'
import ListaContenidos from './paginas/gestion/ListaContenidos'
import NuevoContenido from './paginas/gestion/NuevoContenido'
import EditorContenido from './paginas/gestion/EditorContenido'

export default function App() {
  return (
    <>
      <header className="encabezado">
        <div className="contenedor encabezado-fila">
          <Link to="/" className="marca">XPerience</Link>
          <Link to="/gestion">Cursos y proyectos</Link>
        </div>
      </header>
      <main className="contenedor">
        <Routes>
          <Route path="/gestion" element={<ListaContenidos />} />
          <Route path="/gestion/nuevo" element={<NuevoContenido />} />
          <Route path="/gestion/:id" element={<EditorContenido />} />
          <Route path="*" element={<Navigate to="/gestion" replace />} />
        </Routes>
      </main>
    </>
  )
}
