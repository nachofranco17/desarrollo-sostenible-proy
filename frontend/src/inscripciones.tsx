import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, patch, type CourseType, type EnrollmentItem, type EnrollmentPage } from './api';

// RF4: listado de inscripciones del Talento y actualización de avance.

const TYPES: Record<CourseType, string> = { CURSO: 'Curso', PROYECTO: 'Proyecto' };

function errorText(failure: unknown, fallback: string) {
  return failure instanceof Error ? failure.message : fallback;
}

function EnrollmentRow({ item, onUpdated }: { item: EnrollmentItem; onUpdated: (next: EnrollmentItem) => void }) {
  const [draft, setDraft] = useState(String(item.porcentajeAvance));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    setDraft(String(item.porcentajeAvance));
  }, [item.porcentajeAvance]);

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const value = Number(draft);
      if (!Number.isInteger(value)) {
        throw new Error('El porcentaje debe ser un número entero entre 0 y 100.');
      }
      const updated = await patch<EnrollmentItem>(
        `/enrollments/${item.id}/progress`,
        JSON.stringify({ porcentajeAvance: value }),
      );
      onUpdated(updated);
      setMessage('Avance actualizado.');
    } catch (failure) {
      setError(errorText(failure, 'No se pudo actualizar el avance.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <td>{item.titulo}</td>
      <td>{TYPES[item.tipo]}</td>
      <td>{item.porcentajeAvance}%</td>
      <td>
        <form className="enrollment-progress" onSubmit={save}>
          <label className="sr-only" htmlFor={`avance-${item.id}`}>Nuevo avance</label>
          <input
            id={`avance-${item.id}`}
            type="number"
            min={0}
            max={100}
            step={1}
            value={draft}
            disabled={busy}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className="compact" disabled={busy}>{busy ? 'Guardando…' : 'Actualizar'}</button>
        </form>
        {error && <p className="notice error" role="alert">{error}</p>}
        {message && <p className="notice" role="status">{message}</p>}
      </td>
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

  function replaceItem(next: EnrollmentItem) {
    setItems((current) => current.map((item) => (item.id === next.id ? next : item)));
  }

  return <>
    <span className="eyebrow">MIS INSCRIPCIONES</span>
    <h2>Cursos y proyectos en curso</h2>
    <p className="muted">Acá ves tus inscripciones y podés registrar el porcentaje de avance (0 a 100).</p>
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
            <tr><th>Nombre</th><th>Tipo</th><th>Avance</th><th>Actualizar</th></tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <EnrollmentRow key={item.id} item={item} onUpdated={replaceItem} />
            ))}
          </tbody>
        </table>
      </div>
    )}
  </>;
}
