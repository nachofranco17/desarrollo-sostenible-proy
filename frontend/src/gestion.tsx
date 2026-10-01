import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  api, post, put, del, upload,
  type CourseData, type CourseDetail, type CourseLevel, type CourseState, type CourseSummary, type CourseType,
  type Deliverable, type Lesson,
} from './api';

// RF5: portal de gestión de cursos y proyectos. Qué se muestra depende del estado del contenido;
// quién puede usarlo lo decide el backend en cada solicitud (R9).

const BASE = '/company/courses';
const TYPES: Record<CourseType, string> = { CURSO: 'Curso', PROYECTO: 'Proyecto' };
const LEVELS: Record<CourseLevel, string> = { INICIAL: 'Inicial', INTERMEDIO: 'Intermedio', AVANZADO: 'Avanzado' };
const STATES: Record<CourseState, string> = { BORRADOR: 'Borrador', PUBLICADO: 'Publicado', BAJADO: 'Dado de baja' };
const EMPTY: CourseData = { titulo: '', descripcion: '', tecnologia: '', nivel: 'INICIAL', duracionHoras: 10, costo: 0 };

function errorText(failure: unknown, fallback: string) {
  return failure instanceof Error ? failure.message : fallback;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('es-UY', { dateStyle: 'short', timeStyle: 'short' });
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function StateBadge({ state }: { state: CourseState }) {
  return <span className={`state state-${state.toLowerCase()}`}>{STATES[state]}</span>;
}

/** Botón que pide confirmación en la misma línea antes de una acción destructiva. */
function Confirm({ label, question, onConfirm, disabled, className = 'compact danger' }: {
  label: string; question: string; onConfirm: () => void; disabled?: boolean; className?: string;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) return <button type="button" className={className} disabled={disabled} onClick={() => setAsking(true)}>{label}</button>;
  return <span className="confirm">{question}
    <button type="button" className="compact danger" onClick={() => { setAsking(false); onConfirm(); }}>Sí</button>
    <button type="button" className="compact secondary" onClick={() => setAsking(false)}>No</button>
  </span>;
}

export function CourseList() {
  const [courses, setCourses] = useState<CourseSummary[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    api<CourseSummary[]>(BASE).then(setCourses).catch((failure) => setError(errorText(failure, 'No pudimos cargar los cursos.')));
  }, []);
  return <>
    <span className="eyebrow">GESTIÓN DE CONTENIDO</span>
    <div className="page-title"><h2>Cursos y proyectos</h2><Link className="button-link compact" to="/gestion/nuevo">Nuevo curso o proyecto</Link></div>
    <p className="muted">Creá cursos con lecciones y proyectos con entregables verificables. Se crean como borrador hasta que los publiques.</p>
    {error && <p className="notice error" role="alert">{error}</p>}
    {courses === null && !error && <p role="status">Cargando…</p>}
    {courses?.length === 0 && <p className="empty-hint">Todavía no hay cursos ni proyectos. <Link to="/gestion/nuevo">Crear el primero</Link></p>}
    {courses && courses.length > 0 && <div className="table-wrap"><table className="course-table">
      <thead><tr><th>Título</th><th>Tipo</th><th>Tecnología</th><th>Nivel</th><th>Elementos</th><th>Estado</th><th>Actualizado</th></tr></thead>
      <tbody>{courses.map((c) => <tr key={c.id}>
        <td><Link to={`/gestion/${c.id}`}>{c.titulo}</Link></td><td>{TYPES[c.tipo]}</td><td>{c.tecnologia}</td>
        <td>{LEVELS[c.nivel]}</td><td>{c.cantidadElementos} {c.tipo === 'CURSO' ? 'lecciones' : 'entregables'}</td>
        <td><StateBadge state={c.estado} /></td><td>{formatDate(c.actualizadoEn)}</td>
      </tr>)}</tbody>
    </table></div>}
    <Link className="button-link secondary" to="/mi-cuenta">Volver a mi cuenta</Link>
  </>;
}

function CourseForm({ initial, disabled, busy, submitLabel, onSubmit, children }: {
  initial: CourseData; disabled?: boolean; busy: boolean; submitLabel: string;
  onSubmit: (data: CourseData) => void; children?: ReactNode;
}) {
  const [data, setData] = useState<CourseData>(initial);
  const set = <K extends keyof CourseData>(key: K, value: CourseData[K]) => setData((d) => ({ ...d, [key]: value }));
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ ...data, titulo: data.titulo.trim(), descripcion: data.descripcion.trim(), tecnologia: data.tecnologia.trim() });
  }
  return <form onSubmit={submit}><fieldset className="plain" disabled={disabled || busy}>
    {children}
    <label>Título<input required maxLength={150} value={data.titulo} onChange={(e) => set('titulo', e.target.value)} /></label>
    <label>Descripción<textarea required maxLength={4000} rows={4} value={data.descripcion} onChange={(e) => set('descripcion', e.target.value)} /></label>
    <div className="field-grid">
      <label>Tecnología<input required maxLength={60} placeholder="Java, React, SQL…" value={data.tecnologia} onChange={(e) => set('tecnologia', e.target.value)} /></label>
      <label>Nivel<select value={data.nivel} onChange={(e) => set('nivel', e.target.value as CourseLevel)}>
        {(Object.keys(LEVELS) as CourseLevel[]).map((l) => <option key={l} value={l}>{LEVELS[l]}</option>)}
      </select></label>
      <label>Duración (horas)<input type="number" required min={1} max={1000} value={data.duracionHoras} onChange={(e) => set('duracionHoras', Number(e.target.value))} /></label>
      <label>Costo (USD)<input type="number" required min={0} max={100000} step="0.01" value={data.costo} onChange={(e) => set('costo', Number(e.target.value))} /></label>
    </div>
    {!disabled && <button type="submit" disabled={busy}>{busy ? 'Guardando…' : submitLabel}</button>}
  </fieldset></form>;
}

export function NewCourse() {
  const navigate = useNavigate();
  const [type, setType] = useState<CourseType>('CURSO');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function create(data: CourseData) {
    setBusy(true); setError('');
    try {
      const created = await post<CourseDetail>(BASE, JSON.stringify({ tipo: type, ...data }));
      navigate(`/gestion/${created.id}`, { replace: true });
    } catch (failure) { setError(errorText(failure, 'No se pudo crear el contenido.')); }
    finally { setBusy(false); }
  }
  return <>
    <span className="eyebrow">GESTIÓN DE CONTENIDO</span>
    <h2>Nuevo curso o proyecto</h2>
    <p className="muted">Se crea como borrador. Después vas a poder agregar lecciones, entregables y material antes de publicarlo.</p>
    {error && <p className="notice error" role="alert">{error}</p>}
    <CourseForm initial={EMPTY} busy={busy} submitLabel="Crear borrador" onSubmit={create}>
      <div className="type-options" role="radiogroup" aria-label="Tipo de contenido">
        {(Object.keys(TYPES) as CourseType[]).map((t) => <label key={t} className={type === t ? 'type-option selected' : 'type-option'}>
          <input type="radio" name="tipo" checked={type === t} onChange={() => setType(t)} />
          <strong>{TYPES[t]}</strong>
          <small>{t === 'CURSO' ? 'Lecciones numeradas que el Talento recorre en cualquier orden.' : 'Entregables con respuesta verificable y una pista.'}</small>
        </label>)}
      </div>
    </CourseForm>
    <Link className="button-link secondary" to="/gestion">Volver</Link>
  </>;
}

export function CourseEditor() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [course, setCourse] = useState<CourseDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    api<CourseDetail>(`${BASE}/${id}`).then(setCourse).catch((failure) => setError(errorText(failure, 'No pudimos cargar el contenido.')));
  }, [id]);

  /** Toda operación devuelve el detalle actualizado. */
  async function run(operation: () => Promise<CourseDetail>, done = '') {
    setBusy(true); setError(''); setMessage('');
    try { setCourse(await operation()); setMessage(done); return true; }
    catch (failure) { setError(errorText(failure, 'No se pudo completar la operación.')); return false; }
    finally { setBusy(false); }
  }

  async function remove() {
    setBusy(true); setError('');
    try { await del(`${BASE}/${id}`); navigate('/gestion', { replace: true }); }
    catch (failure) { setError(errorText(failure, 'No se pudo eliminar.')); setBusy(false); }
  }

  if (!course) return <>{error ? <p className="notice error" role="alert">{error}</p> : <p role="status">Cargando…</p>}<Link className="button-link secondary" to="/gestion">Volver</Link></>;

  const editable = course.estado !== 'BAJADO';
  const path = `${BASE}/${course.id}`;
  return <>
    <span className="eyebrow">{TYPES[course.tipo].toUpperCase()} · ACTUALIZADO {formatDate(course.actualizadoEn)}</span>
    <div className="page-title"><h2>{course.titulo} <StateBadge state={course.estado} /></h2>
      <div className="actions">
        {course.estado !== 'PUBLICADO' && <button type="button" className="compact" disabled={busy}
          onClick={() => void run(() => post(`${path}/publish`), 'Publicado.')}>{course.estado === 'BAJADO' ? 'Volver a publicar' : 'Publicar'}</button>}
        {course.estado === 'PUBLICADO' && <Confirm label="Dar de baja" className="compact secondary" disabled={busy}
          question="Deja de admitir inscripciones; los inscriptos conservan el acceso. ¿Confirmás?"
          onConfirm={() => void run(() => post(`${path}/unpublish`), 'Dado de baja.')} />}
        {course.estado === 'BORRADOR' && <Confirm label="Eliminar" disabled={busy} question="¿Eliminar definitivamente este borrador?" onConfirm={() => void remove()} />}
      </div>
    </div>
    {message && <p className="notice" role="status">{message}</p>}
    {error && <p className="notice error" role="alert">{error}</p>}
    {course.estado === 'BAJADO' && <p className="notice warning">Está dado de baja: no admite inscripciones nuevas ni modificaciones.</p>}
    {course.estado === 'PUBLICADO' && <p className="notice warning">Está publicado: podés editarlo y agregar elementos, pero no eliminar lecciones ni entregables, porque puede haber Talentos avanzando en ellos.</p>}

    <section className="profile-section" aria-label="Datos generales">
      <h3>Datos generales</h3>
      <CourseForm key={course.actualizadoEn} initial={course} disabled={!editable} busy={busy} submitLabel="Guardar cambios"
        onSubmit={(data) => void run(() => put(path, JSON.stringify(data)), 'Cambios guardados.')} />
    </section>

    {course.tipo === 'CURSO'
      ? <Lessons course={course} editable={editable} busy={busy} run={run} />
      : <Deliverables course={course} editable={editable} busy={busy} run={run} />}
    <Materials course={course} editable={editable} busy={busy} run={run} />
    <Link className="button-link secondary" to="/gestion">Volver a cursos y proyectos</Link>
  </>;
}

type SectionProps = {
  course: CourseDetail; editable: boolean; busy: boolean;
  run: (operation: () => Promise<CourseDetail>, done?: string) => Promise<boolean>;
};

function Lessons({ course, editable, busy, run }: SectionProps) {
  const [editing, setEditing] = useState<string | null>(null);
  const path = `${BASE}/${course.id}/lessons`;
  return <section className="profile-section" aria-label="Lecciones">
    <h3>Lecciones ({course.lecciones.length})</h3>
    <p className="empty-hint section-note">El Talento puede recorrerlas en cualquier orden y marcarlas como completadas.</p>
    {course.lecciones.length === 0 && <p className="empty-hint">Agregá al menos una lección para poder publicar el curso.</p>}
    <ol className="items">{course.lecciones.map((l) => <li key={l.id} className="item">
      {editing === l.id
        ? <LessonForm initial={l} busy={busy} submitLabel="Guardar" onCancel={() => setEditing(null)}
            onSubmit={async (data) => { if (await run(() => put(`${path}/${l.id}`, JSON.stringify(data)))) setEditing(null); }} />
        : <ItemView number={l.numero} title={l.titulo} body={l.cuerpo}>
            {editable && <button type="button" className="compact secondary" onClick={() => setEditing(l.id)}>Editar</button>}
            {editable && course.estado === 'BORRADOR' && <Confirm label="Eliminar" question="¿Eliminar la lección?" onConfirm={() => void run(() => del(`${path}/${l.id}`))} />}
          </ItemView>}
    </li>)}</ol>
    {editable && <details className="collapse-block"><summary>Agregar lección</summary><div className="collapse-body">
      <LessonForm busy={busy} submitLabel="Agregar lección" resetOnSuccess onSubmit={(data) => run(() => post(path, JSON.stringify(data)))} />
    </div></details>}
  </section>;
}

function ItemView({ number, title, body, children }: { number: number; title: string; body: string; children?: ReactNode }) {
  return <>
    <div className="item-head"><strong>{number}. {title}</strong><span className="actions">{children}</span></div>
    <p className="item-body">{body}</p>
  </>;
}

function LessonForm({ initial, busy, submitLabel, onSubmit, onCancel, resetOnSuccess }: {
  initial?: Pick<Lesson, 'titulo' | 'cuerpo'>; busy: boolean; submitLabel: string;
  onSubmit: (data: { titulo: string; cuerpo: string }) => Promise<unknown>; onCancel?: () => void; resetOnSuccess?: boolean;
}) {
  const [titulo, setTitulo] = useState(initial?.titulo ?? '');
  const [cuerpo, setCuerpo] = useState(initial?.cuerpo ?? '');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (await onSubmit({ titulo: titulo.trim(), cuerpo }) && resetOnSuccess) { setTitulo(''); setCuerpo(''); }
  }
  return <form onSubmit={submit}><fieldset className="plain" disabled={busy}>
    <label>Título<input required maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} /></label>
    <label>Contenido<textarea required maxLength={20000} rows={6} value={cuerpo} onChange={(e) => setCuerpo(e.target.value)} /></label>
    <div className="actions"><button type="submit" className="compact">{submitLabel}</button>
      {onCancel && <button type="button" className="compact secondary" onClick={onCancel}>Cancelar</button>}</div>
  </fieldset></form>;
}

function Deliverables({ course, editable, busy, run }: SectionProps) {
  const [editing, setEditing] = useState<string | null>(null);
  const path = `${BASE}/${course.id}/deliverables`;
  return <section className="profile-section" aria-label="Entregables">
    <h3>Entregables ({course.entregables.length})</h3>
    <p className="empty-hint section-note">El Talento envía su respuesta y el sistema la compara contra las aceptadas, sin distinguir mayúsculas ni espacios de más. Si no coincide, ve la pista. Las respuestas aceptadas se guardan como hash: nadie puede volver a leerlas, solo reemplazarlas.</p>
    {course.entregables.length === 0 && <p className="empty-hint">Agregá al menos un entregable para poder publicar el proyecto.</p>}
    <ol className="items">{course.entregables.map((d) => <li key={d.id} className="item">
      {editing === d.id
        ? <DeliverableForm initial={d} busy={busy} submitLabel="Guardar" onCancel={() => setEditing(null)}
            onSubmit={async (data) => { if (await run(() => put(`${path}/${d.id}`, JSON.stringify(data)))) setEditing(null); }} />
        : <ItemView number={d.numero} title={d.titulo} body={d.consigna}>
            {editable && <button type="button" className="compact secondary" onClick={() => setEditing(d.id)}>Editar</button>}
            {editable && course.estado === 'BORRADOR' && <Confirm label="Eliminar" question="¿Eliminar el entregable?" onConfirm={() => void run(() => del(`${path}/${d.id}`))} />}
          </ItemView>}
      {editing !== d.id && <p className="empty-hint">Pista: {d.pista} · {d.cantidadRespuestasAceptadas} {d.cantidadRespuestasAceptadas === 1 ? 'respuesta aceptada' : 'respuestas aceptadas'}</p>}
    </li>)}</ol>
    {editable && <details className="collapse-block"><summary>Agregar entregable</summary><div className="collapse-body">
      <DeliverableForm busy={busy} submitLabel="Agregar entregable" resetOnSuccess onSubmit={(data) => run(() => post(path, JSON.stringify(data)))} />
    </div></details>}
  </section>;
}

function DeliverableForm({ initial, busy, submitLabel, onSubmit, onCancel, resetOnSuccess }: {
  initial?: Deliverable; busy: boolean; submitLabel: string;
  onSubmit: (data: Record<string, unknown>) => Promise<unknown>; onCancel?: () => void; resetOnSuccess?: boolean;
}) {
  const [titulo, setTitulo] = useState(initial?.titulo ?? '');
  const [consigna, setConsigna] = useState(initial?.consigna ?? '');
  const [pista, setPista] = useState(initial?.pista ?? '');
  const [answers, setAnswers] = useState('');
  // Al editar, las respuestas se conservan salvo que se pida reemplazarlas.
  const [replace, setReplace] = useState(!initial);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data: Record<string, unknown> = { titulo: titulo.trim(), consigna: consigna.trim(), pista: pista.trim() };
    if (replace) data.respuestasAceptadas = answers.split('\n').map((a) => a.trim()).filter(Boolean);
    if (await onSubmit(data) && resetOnSuccess) { setTitulo(''); setConsigna(''); setPista(''); setAnswers(''); }
  }
  return <form onSubmit={submit}><fieldset className="plain" disabled={busy}>
    <label>Título<input required maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} /></label>
    <label>Consigna<textarea required maxLength={4000} rows={4} value={consigna} onChange={(e) => setConsigna(e.target.value)} /></label>
    <label>Pista si la respuesta no coincide<input required maxLength={500} value={pista} onChange={(e) => setPista(e.target.value)} /></label>
    {initial && <label className="checkbox"><input type="checkbox" checked={replace} onChange={(e) => setReplace(e.target.checked)} />
      Reemplazar las {initial.cantidadRespuestasAceptadas} respuestas aceptadas actuales</label>}
    {replace && <label>Respuestas aceptadas (una por línea, hasta 10)
      <textarea required rows={3} value={answers} onChange={(e) => setAnswers(e.target.value)} autoComplete="off" spellCheck={false} /></label>}
    <div className="actions"><button type="submit" className="compact">{submitLabel}</button>
      {onCancel && <button type="button" className="compact secondary" onClick={onCancel}>Cancelar</button>}</div>
  </fieldset></form>;
}

function Materials({ course, editable, busy, run }: SectionProps) {
  const input = useRef<HTMLInputElement>(null);
  const path = `${BASE}/${course.id}/materials`;
  async function send(file: File | undefined) {
    if (!file) return;
    const form = new FormData();
    form.append('archivo', file);
    await run(() => upload(path, form), 'Archivo subido.');
    if (input.current) input.current.value = '';
  }
  return <section className="profile-section" aria-label="Material">
    <h3>Material ({course.materiales.length})</h3>
    <p className="empty-hint section-note">PDF, PNG, JPG, ZIP o MP4, hasta 25 MB. Solo se descarga a través de la plataforma.</p>
    {course.materiales.length === 0 && <p className="empty-hint">Sin material cargado.</p>}
    <ul className="files">{course.materiales.map((m) => <li key={m.id}>
      <a href={`/api${path}/${m.id}`}>{m.nombre}</a>
      <span className="empty-hint">{formatSize(m.tamanioBytes)} · {formatDate(m.subidoEn)}</span>
      {editable && <Confirm label="Eliminar" question="¿Eliminar el archivo?" disabled={busy} onConfirm={() => void run(() => del(`${path}/${m.id}`))} />}
    </li>)}</ul>
    {editable && <label>Subir archivo<input ref={input} type="file" disabled={busy}
      accept=".pdf,.png,.jpg,.jpeg,.zip,.mp4" onChange={(e) => void send(e.target.files?.[0])} /></label>}
  </section>;
}
