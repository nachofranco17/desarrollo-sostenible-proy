import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  api, catalogQuery, TECHNOLOGY_OPTIONS,
  type CatalogDetail, type CatalogFilters, type CatalogPage, type CourseLevel, type CourseType,
} from './api';

// RF3: catálogo público de cursos y proyectos publicados.

const BASE = '/catalog/courses';
const TYPES: Record<CourseType, string> = { CURSO: 'Curso', PROYECTO: 'Proyecto' };
const LEVELS: Record<CourseLevel, string> = { INICIAL: 'Inicial', INTERMEDIO: 'Intermedio', AVANZADO: 'Avanzado' };
const EMPTY: CatalogFilters = { q: '', tecnologia: '', nivel: '', duracionMin: '', duracionMax: '', costoMin: '', costoMax: '' };

function errorText(failure: unknown, fallback: string) {
  return failure instanceof Error ? failure.message : fallback;
}

function formatCost(value: number) {
  return new Intl.NumberFormat('es-UY', { style: 'currency', currency: 'UYU' }).format(value);
}

function technologyChoices(fromApi: string[] = []) {
  return [...new Set([...TECHNOLOGY_OPTIONS, ...fromApi])].sort((a, b) => a.localeCompare(b, 'es'));
}

export function CatalogList() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<CatalogFilters>(EMPTY);
  const [draft, setDraft] = useState<CatalogFilters>(EMPTY);
  const [page, setPage] = useState<CatalogPage | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api<CatalogPage>(BASE + catalogQuery(filters))
      .then((result) => { if (!cancelled) setPage(result); })
      .catch((failure) => { if (!cancelled) { setPage(null); setError(errorText(failure, 'No se pudo cargar el catálogo.')); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [filters]);

  function apply(event: FormEvent) {
    event.preventDefault();
    setFilters({ ...draft });
  }

  function clear() {
    setDraft(EMPTY);
    setFilters(EMPTY);
  }

  const technologies = technologyChoices(page?.tecnologiasDisponibles);
  const items = page?.items ?? [];

  return <>
    <span className="eyebrow">CATÁLOGO</span>
    <h2>Cursos y proyectos</h2>
    <p className="muted">Explorá la oferta publicada. Podés buscar por nombre y combinar filtros.</p>

    <form className="catalog-filters" onSubmit={apply}>
      <label>Buscar por nombre
        <input value={draft.q ?? ''} maxLength={150} placeholder="Ej. Java desde cero"
          onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
      </label>
      <div className="field-grid">
        <label>Tecnología
          <select className="field-select" value={draft.tecnologia ?? ''}
            onChange={(e) => setDraft({ ...draft, tecnologia: e.target.value })}>
            <option value="">Todas</option>
            {technologies.map((tech) => <option key={tech} value={tech}>{tech}</option>)}
          </select>
        </label>
        <label>Nivel
          <select className="field-select" value={draft.nivel ?? ''}
            onChange={(e) => setDraft({ ...draft, nivel: e.target.value as CourseLevel | '' })}>
            <option value="">Todos</option>
            {(Object.keys(LEVELS) as CourseLevel[]).map((level) => <option key={level} value={level}>{LEVELS[level]}</option>)}
          </select>
        </label>
        <label>Duración mín. (h)
          <input type="number" min={1} max={1000} value={draft.duracionMin ?? ''}
            onChange={(e) => setDraft({ ...draft, duracionMin: e.target.value })} />
        </label>
        <label>Duración máx. (h)
          <input type="number" min={1} max={1000} value={draft.duracionMax ?? ''}
            onChange={(e) => setDraft({ ...draft, duracionMax: e.target.value })} />
        </label>
        <label>Costo mín.
          <input type="number" min={0} max={100000} step="0.01" value={draft.costoMin ?? ''}
            onChange={(e) => setDraft({ ...draft, costoMin: e.target.value })} />
        </label>
        <label>Costo máx.
          <input type="number" min={0} max={100000} step="0.01" value={draft.costoMax ?? ''}
            onChange={(e) => setDraft({ ...draft, costoMax: e.target.value })} />
        </label>
      </div>
      <div className="catalog-actions">
        <button type="submit">Aplicar filtros</button>
        <button type="button" className="secondary" onClick={clear}>Limpiar filtros</button>
      </div>
    </form>

    {error && <p className="notice error" role="alert">{error}</p>}
    {loading && <p role="status">Cargando catálogo…</p>}
    {!loading && !error && items.length === 0 && (
      <p className="notice" role="status">No hay cursos ni proyectos que coincidan con los filtros.</p>
    )}
    {!loading && items.length > 0 && (
      <div className="table-wrap">
        <table className="course-table">
          <thead>
            <tr><th>Nombre</th><th>Tipo</th><th>Tecnología</th><th>Nivel</th><th>Duración</th><th>Costo</th><th></th></tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>{item.titulo}</td>
                <td>{TYPES[item.tipo]}</td>
                <td>{item.tecnologia}</td>
                <td>{LEVELS[item.nivel]}</td>
                <td>{item.duracionHoras} h</td>
                <td>{formatCost(item.costo)}</td>
                <td><button type="button" className="compact" onClick={() => navigate(`/catalogo/${item.id}`)}>Ver</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </>;
}

export function CatalogDetailPage() {
  const { id } = useParams();
  const [detail, setDetail] = useState<CatalogDetail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    api<CatalogDetail>(`${BASE}/${id}`)
      .then((result) => { if (!cancelled) setDetail(result); })
      .catch((failure) => { if (!cancelled) { setDetail(null); setError(errorText(failure, 'No se pudo abrir el detalle.')); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  return <>
    <p><Link to="/catalogo">← Volver al catálogo</Link></p>
    {loading && <p role="status">Cargando…</p>}
    {error && <p className="notice error" role="alert">{error}</p>}
    {detail && <>
      <span className="eyebrow">{TYPES[detail.tipo].toUpperCase()}</span>
      <h2>{detail.titulo}</h2>
      <p className="muted">{detail.descripcion}</p>
      <dl>
        <dt>Tipo</dt><dd>{TYPES[detail.tipo]}</dd>
        <dt>Tecnología</dt><dd>{detail.tecnologia}</dd>
        <dt>Nivel</dt><dd>{LEVELS[detail.nivel]}</dd>
        <dt>Duración</dt><dd>{detail.duracionHoras} horas</dd>
        <dt>Costo</dt><dd>{formatCost(detail.costo)}</dd>
        <dt>Empresa</dt><dd>{detail.empresaNombre}</dd>
      </dl>
    </>}
  </>;
}
