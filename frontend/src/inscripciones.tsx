import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type CourseType, type EnrollmentItem, type EnrollmentPage } from './api';

// RF4: listado de inscripciones del Talento. El avance es de solo lectura: lo calcula el backend (DS-33).

const TYPES: Record<CourseType, string> = { CURSO: 'Curso', PROYECTO: 'Proyecto' };

function errorText(failure: unknown, fallback: string) {
  return failure instanceof Error ? failure.message : fallback;
}

function EnrollmentRow({ item }: { item: EnrollmentItem }) {
  return (
    <tr>
      <td>{item.titulo}</td>
      <td>{TYPES[item.tipo]}</td>
      <td>{item.porcentajeAvance}%</td>
    </tr>
  );
}

export function EnrollmentsPage() {
  const [items, setItems] = useState<EnrollmentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api<EnrollmentPage>('/enrollments/me')
      .then((page) => { if (!cancelled) setItems(page.items); })
      .catch((failure) => {
        if (!cancelled) {
          setItems([]);
          setError(errorText(failure, 'No se pudieron cargar tus inscripciones.'));
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  return <>
    <span className="eyebrow">MIS INSCRIPCIONES</span>
    <h2>Cursos y proyectos en curso</h2>
    <p className="muted">Acá ves tus inscripciones y el avance de cada una. El avance se calcula a partir de lo que completás en el curso o proyecto.</p>
    <p><Link to="/catalogo">Ir al catálogo</Link></p>
    {loading && <p role="status">Cargando…</p>}
    {error && <p className="notice error" role="alert">{error}</p>}
    {!loading && !error && items.length === 0 && (
      <p className="notice" role="status">Todavía no tenés inscripciones. Explorá el catálogo para anotarte.</p>
    )}
    {!loading && items.length > 0 && (
      <div className="table-wrap">
        <table className="course-table">
          <thead>
            <tr><th>Nombre</th><th>Tipo</th><th>Avance</th></tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <EnrollmentRow key={item.id} item={item} />
            ))}
          </tbody>
        </table>
      </div>
    )}
  </>;
}
