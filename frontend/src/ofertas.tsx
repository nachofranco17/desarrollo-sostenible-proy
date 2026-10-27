import { useContext, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, post, put, upload, ApiError, type CatalogDetail, type CatalogPage } from './api';
import { Auth } from './auth';

type OfferSummary = { id: string; titulo: string; estado: 'BORRADOR' | 'PUBLICADA'; actualizadoEn: string };
type OfferDetail = OfferSummary & { descripcion: string; cursosRequeridos: string[]; creadoEn: string };
type Curriculum = { id: string; nombreOriginal: string; tamanioBytes: number; creadoEn: string };
type OwnApplication = { id: string; ofertaId: string; ofertaTitulo: string; empresaNombre: string; estado: string; fecha: string };
type ApplicantProfile = { nombre: string; apellido: string; correo: string; especializaciones: string[] };
type Applicant = { id: string; estado: string; fecha: string; perfil: ApplicantProfile };

const date = (value: string) => new Date(value).toLocaleDateString('es-UY');
const encoded = (value: string) => encodeURIComponent(value);
const message = (error: unknown) => error instanceof Error ? error.message : 'No se pudo completar la solicitud.';

/** Limpia el resultado anterior al cambiar de recurso, usuario, empresa o rol. */
function useRemote<T>(path: string | null, missingIsEmpty = false) {
  const auth = useContext(Auth);
  const [revision, setRevision] = useState(0);
  const key = JSON.stringify([path, revision, auth.account?.id, auth.account?.rol, auth.account?.empresaId, missingIsEmpty]);
  const [result, setResult] = useState<{ key: string; data: T | null; loading: boolean; error: string }>(
    { key, data: null, loading: Boolean(path), error: '' });
  useEffect(() => {
    let cancelled = false;
    setResult({ key, data: null, error: '', loading: Boolean(path) });
    if (!path) return;
    api<T>(path).then((data) => { if (!cancelled) setResult({ key, data, error: '', loading: false }); })
      .catch((failure) => {
        if (cancelled) return;
        const empty = missingIsEmpty && failure instanceof ApiError && failure.status === 404;
        setResult({ key, data: null, error: empty ? '' : message(failure), loading: false });
        if (failure instanceof ApiError && [401, 403].includes(failure.status)) void auth.refresh().catch(() => {});
      });
    return () => { cancelled = true; };
  }, [key]);
  const current = result.key === key ? result : { data: null, loading: Boolean(path), error: '' };
  return { data: current.data, loading: current.loading, error: current.error,
    reload: () => setRevision((value) => value + 1),
    clear: () => setResult((previous) => ({ ...previous, data: null })) };
}

function useAction(onDenied?: () => void) {
  const auth = useContext(Auth);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true); setError('');
    try { await action(); }
    catch (failure) {
      setError(message(failure));
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) {
        onDenied?.();
        void auth.refresh().catch(() => {});
      }
    } finally { setBusy(false); }
  }
  return { run, busy, error };
}

function Notice({ loading, error }: { loading: boolean; error: string }) {
  return <>{loading && <p role="status">Cargando…</p>}{error && <p className="notice error" role="alert">{error}</p>}</>;
}

function RequiredCourses({ ids }: { ids: string[] }) {
  const [items, setItems] = useState<(CatalogDetail | null)[]>([]);
  const key = ids.join(',');
  useEffect(() => {
    let cancelled = false;
    setItems([]);
    void Promise.all(ids.map((id) => api<CatalogDetail>('/catalog/courses/' + encoded(id)).catch(() => null)))
      .then((next) => { if (!cancelled) setItems(next); });
    return () => { cancelled = true; };
  }, [key]);
  if (!ids.length) return <p className="muted">Esta oferta no exige cursos o proyectos específicos.</p>;
  return <ul className="requirement-list">{ids.map((id, index) =>
    <li key={id}>{items[index] ? <Link to={'/catalogo/' + encoded(id)}>{items[index]!.titulo}</Link>
      : <span className="muted">{items[index] === null ? 'Curso o proyecto no disponible en el catálogo actual' : 'Cargando requisito…'}</span>}</li>)}</ul>;
}

export function OfferList() {
  const offers = useRemote<OfferSummary[]>('/offers');
  return <>
    <span className="eyebrow">OPORTUNIDADES</span><h2>Ofertas laborales</h2>
    <p className="muted">Explorá las ofertas publicadas y conocé los requisitos.</p>
    <Notice {...offers} />
    {offers.data?.length === 0 && <p className="notice">Todavía no hay ofertas publicadas.</p>}
    <div className="offer-grid">{offers.data?.map((offer) => <article className="offer-card" key={offer.id}>
      <span className="badge">Publicada</span><h3>{offer.titulo}</h3>
      <p className="muted">Actualizada el {date(offer.actualizadoEn)}</p>
      <Link className="button-link" to={'/ofertas/' + encoded(offer.id)}>Ver oferta</Link>
    </article>)}</div>
  </>;
}

export function OfferDetailPage() {
  const { id = '' } = useParams();
  const auth = useContext(Auth);
  const talent = auth.account?.rol === 'TALENTO';
  const offer = useRemote<OfferDetail>('/offers/' + encoded(id));
  const cv = useRemote<Curriculum>(talent ? '/profile/me/curriculum/metadata' : null, true);
  const applications = useRemote<OwnApplication[]>(talent ? '/applications/me' : null);
  const action = useAction(offer.clear);
  const [success, setSuccess] = useState('');
  useEffect(() => { setSuccess(''); }, [id]);
  const applied = applications.data?.some((item) => item.ofertaId === id);
  return <>
    <span className="eyebrow">OFERTA LABORAL</span><Notice {...offer} />
    {offer.data && <>
      <h2>{offer.data.titulo}</h2><p className="offer-description">{offer.data.descripcion}</p>
      <h3>Cursos y proyectos requeridos</h3><RequiredCourses ids={offer.data.cursosRequeridos} />
      {talent && <section className="profile-section" aria-label="Postularme">
        <h3>Tu postulación</h3><Notice loading={cv.loading || applications.loading} error={cv.error || applications.error} />
        {applied || success ? <><p className="notice" role="status">{success || 'Ya te postulaste a esta oferta.'}</p>
          <Link to="/mis-postulaciones">Ver mis postulaciones</Link></> : <>
          <p>{cv.data ? 'Se adjuntará tu currículum actual: ' + cv.data.nombreOriginal : 'Cargá tu currículum en PDF antes de postularte.'}</p>
          <p><Link to="/mi-curriculum">{cv.data ? 'Revisar o reemplazar mi currículum' : 'Cargar mi currículum'}</Link></p>
          <button disabled={action.busy || !cv.data || applications.loading || Boolean(applications.error)}
            onClick={() => void action.run(async () => {
              await post<OwnApplication>('/offers/' + encoded(id) + '/applications', '{}');
              setSuccess('Tu postulación quedó registrada.'); applications.reload();
            })}>{action.busy ? 'Enviando…' : 'Postularme'}</button>
        </>}
        {action.error && <p className="notice error" role="alert">{action.error}</p>}
      </section>}
    </>}
  </>;
}

export function CompanyOffers() {
  const offers = useRemote<OfferSummary[]>('/company/offers');
  return <>
    <span className="eyebrow">MI EMPRESA</span><h2>Mis ofertas laborales</h2>
    <p><Link className="button-link" to="/gestion-ofertas/nueva">Crear oferta laboral</Link></p>
    <Notice {...offers} />
    {offers.data?.length === 0 && <p className="notice">Todavía no creaste ofertas laborales.</p>}
    {Boolean(offers.data?.length) && <div className="table-wrap"><table className="course-table">
      <thead><tr><th>Oferta</th><th>Estado</th><th>Última actualización</th><th>Acciones</th></tr></thead>
      <tbody>{offers.data?.map((offer) => <tr key={offer.id}><td>{offer.titulo}</td>
        <td>{offer.estado === 'BORRADOR' ? 'Borrador' : 'Publicada'}</td><td>{date(offer.actualizadoEn)}</td>
        <td><Link to={'/gestion-ofertas/' + encoded(offer.id)}>{offer.estado === 'BORRADOR' ? 'Editar oferta' : 'Ver oferta'}</Link>
          {' · '}<Link to={'/gestion-ofertas/' + encoded(offer.id) + '/postulantes'}>Ver postulantes</Link></td>
      </tr>)}</tbody></table></div>}
  </>;
}

function RequirementPicker({ selected, change, disabled }: { selected: string[]; change: (next: string[]) => void; disabled: boolean }) {
  const [query, setQuery] = useState('');
  const catalog = useRemote<CatalogPage>('/catalog/courses' + (query.trim() ? '?q=' + encoded(query.trim()) : ''));
  return <fieldset className="requirement-picker" disabled={disabled}>
    <legend>Cursos y proyectos requeridos</legend>
    <p className="muted">Podés elegir contenidos publicados de cualquier empresa. Los requisitos son opcionales.</p>
    <label>Buscar un curso o proyecto<input type="search" value={query} onChange={(event) => setQuery(event.target.value)} maxLength={150} /></label>
    <Notice {...catalog} />
    <div className="requirement-options">{catalog.data?.items.map((item) =>
      <label className="requirement-option" key={item.id}><input type="checkbox" checked={selected.includes(item.id)}
        disabled={!selected.includes(item.id) && selected.length >= 100}
        onChange={(event) => change(event.target.checked ? [...selected, item.id] : selected.filter((id) => id !== item.id))} />
        <span>{item.titulo}<small>{item.tipo === 'CURSO' ? 'Curso' : 'Proyecto'} · {item.empresaNombre}</small></span></label>)}</div>
    {catalog.data?.items.length === 0 && <p>No hay contenidos publicados que coincidan con la búsqueda.</p>}
    <p className="muted">{selected.length} requisitos seleccionados</p>
    {selected.length > 0 && <><RequiredCourses ids={selected} />
      <button type="button" className="secondary compact" onClick={() => change([])}>Quitar todos los requisitos</button></>}
  </fieldset>;
}

export function OfferEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const offer = useRemote<OfferDetail>(id ? '/company/offers/' + encoded(id) : null);
  const action = useAction(offer.clear);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [requirements, setRequirements] = useState<string[]>([]);
  const [success, setSuccess] = useState('');
  useEffect(() => {
    setTitle(offer.data?.titulo ?? ''); setDescription(offer.data?.descripcion ?? '');
    setRequirements(offer.data?.cursosRequeridos ?? []);
  }, [offer.data, id]);
  useEffect(() => { setSuccess(''); }, [id]);
  const published = offer.data?.estado === 'PUBLICADA';
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const publish = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'publish';
    setSuccess('');
    void action.run(async () => {
      const body = JSON.stringify({ titulo: title.trim(), descripcion: description.trim(), cursosRequeridos: requirements });
      const saved = id ? await put<OfferDetail>('/company/offers/' + encoded(id), body)
        : await post<OfferDetail>('/company/offers', body);
      if (publish) await post<OfferDetail>('/company/offers/' + encoded(saved.id) + '/publish', '{}');
      if (!id) navigate('/gestion-ofertas/' + encoded(saved.id), { replace: true });
      else { offer.reload(); setSuccess(publish ? 'Oferta publicada.' : 'Borrador guardado.'); }
    });
  }
  return <>
    <span className="eyebrow">MI EMPRESA</span><h2>{id ? 'Oferta laboral' : 'Crear oferta laboral'}</h2>
    <Notice {...offer} />
    {action.error && <p className="notice error" role="alert">{action.error}</p>}
    {success && <p className="notice" role="status">{success}</p>}
    {id && !offer.data ? null : published ? <>
      <span className="badge">Publicada</span><h3>{offer.data!.titulo}</h3>
      <p className="offer-description">{offer.data!.descripcion}</p>
      <h3>Requisitos</h3><RequiredCourses ids={offer.data!.cursosRequeridos} />
      <p><Link className="button-link" to={'/gestion-ofertas/' + encoded(id!) + '/postulantes'}>Ver postulantes</Link></p>
    </> : <form onSubmit={submit}>
      <label>Título de la oferta<input required maxLength={150} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label>Descripción de la oferta<textarea required maxLength={4000} rows={6} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <RequirementPicker selected={requirements} change={setRequirements} disabled={action.busy} />
      <div className="offer-actions"><button type="submit" disabled={action.busy}>{action.busy ? 'Guardando…' : 'Guardar borrador'}</button>
        {id && <button type="submit" name="intent" value="publish" disabled={action.busy}>Publicar oferta</button>}</div>
      <p className="muted">Revisá el contenido antes de publicar. Una oferta publicada ya no se puede editar.</p>
    </form>}
  </>;
}

export function CurriculumPage() {
  const cv = useRemote<Curriculum>('/profile/me/curriculum/metadata', true);
  const action = useAction(cv.clear);
  const [success, setSuccess] = useState('');
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSuccess('');
    void action.run(async () => {
      const file = data.get('archivo');
      if (!(file instanceof File) || !file.size) throw new Error('Elegí un archivo PDF.');
      if (file.size > 5 * 1024 * 1024) throw new Error('El currículum no puede superar los 5 MiB.');
      await upload<Curriculum>('/profile/me/curriculum', data);
      form.reset(); cv.reload(); setSuccess('Tu currículum quedó guardado.');
    });
  }
  return <>
    <span className="eyebrow">MI PERFIL PROFESIONAL</span><h2>Mi currículum</h2>
    <Notice {...cv} />
    {cv.data && <section className="profile-section"><h3>Currículum actual</h3><p>{cv.data.nombreOriginal}</p>
      <p className="muted">{Math.ceil(cv.data.tamanioBytes / 1024)} KB · Cargado el {date(cv.data.creadoEn)}</p>
      <a href="/api/profile/me/curriculum">Descargar mi currículum</a></section>}
    {!cv.loading && !cv.data && !cv.error && <p className="notice">Todavía no cargaste tu currículum.</p>}
    <p className="muted">Subí un PDF de hasta 5 MiB. Las postulaciones que ya enviaste conservan el CV que adjuntaste en ese momento.</p>
    <form onSubmit={submit}><label>Archivo de currículum<input name="archivo" type="file" accept=".pdf,application/pdf" required /></label>
      {action.error && <p className="notice error" role="alert">{action.error}</p>}
      {success && <p className="notice" role="status">{success}</p>}
      <button disabled={action.busy} type="submit">{action.busy ? 'Guardando…' : cv.data ? 'Reemplazar currículum' : 'Guardar currículum'}</button></form>
    <p><Link to="/ofertas">Explorar ofertas laborales</Link></p>
  </>;
}

export function MyApplications() {
  const applications = useRemote<OwnApplication[]>('/applications/me');
  return <>
    <span className="eyebrow">MIS OPORTUNIDADES</span><h2>Mis postulaciones</h2>
    <Notice {...applications} />
    {applications.data?.length === 0 && <p className="notice">Todavía no te postulaste a ninguna oferta.</p>}
    {Boolean(applications.data?.length) && <div className="table-wrap"><table className="course-table">
      <thead><tr><th>Oferta</th><th>Empresa</th><th>Estado</th><th>Fecha</th></tr></thead>
      <tbody>{applications.data?.map((item) => <tr key={item.id}><td><Link to={'/ofertas/' + encoded(item.ofertaId)}>{item.ofertaTitulo}</Link></td>
        <td>{item.empresaNombre}</td><td>Enviada</td><td>{date(item.fecha)}</td></tr>)}</tbody></table></div>}
    <p><Link to="/ofertas">Ver ofertas laborales</Link></p>
  </>;
}

export function ApplicantsPage() {
  const { id = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const base = '/company/offers/' + encoded(id);
  const offer = useRemote<OfferDetail>(base);
  const applicants = useRemote<Applicant[]>(base + '/applications' + (query ? '?' + query : ''));
  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ['q', 'especializacion', 'fechaDesde', 'fechaHasta']) {
      const value = String(values.get(key) ?? '').trim();
      if (value) next.set(key, value);
    }
    setParams(next);
  }
  return <>
    <span className="eyebrow">MI EMPRESA</span><h2>Postulantes</h2>
    {offer.data && <h3>{offer.data.titulo}</h3>}
    <form className="applicant-filters" onSubmit={filter} key={id + '?' + query}>
      <label>Nombre o apellido<input name="q" type="search" defaultValue={params.get('q') ?? ''} maxLength={150} /></label>
      <label>Especialización<input name="especializacion" defaultValue={params.get('especializacion') ?? ''} placeholder="Ej. Backend" maxLength={80} /></label>
      <label>Fecha desde<input name="fechaDesde" type="date" defaultValue={params.get('fechaDesde') ?? ''} /></label>
      <label>Fecha hasta<input name="fechaHasta" type="date" defaultValue={params.get('fechaHasta') ?? ''} /></label>
      <div className="offer-actions"><button type="submit" disabled={applicants.loading}>Aplicar filtros</button>
        <button type="button" className="secondary" onClick={() => setParams({})}>Limpiar filtros</button></div>
    </form>
    <Notice loading={offer.loading || applicants.loading} error={offer.error || applicants.error} />
    {applicants.data?.length === 0 && <p className="notice" role="status">No hay postulaciones que coincidan con estos filtros.</p>}
    {Boolean(applicants.data?.length) && <div className="table-wrap"><table className="course-table">
      <thead><tr><th>Talento</th><th>Correo</th><th>Especializaciones</th><th>Fecha</th><th>Acciones</th></tr></thead>
      <tbody>{applicants.data?.map((item) => <tr key={item.id}>
        <td>{item.perfil.nombre} {item.perfil.apellido}</td><td>{item.perfil.correo}</td>
        <td>{item.perfil.especializaciones.join(', ') || 'Sin cargar'}</td><td>{date(item.fecha)}</td>
        <td><Link to={'/gestion-ofertas/' + encoded(id) + '/postulantes/' + encoded(item.id) + '/perfil' + (query ? '?' + query : '')}>Ver perfil</Link>
          {' · '}<a href={'/api' + base + '/applications/' + encoded(item.id) + '/curriculum'}>Descargar CV</a></td>
      </tr>)}</tbody></table></div>}
  </>;
}

export function ApplicantProfilePage() {
  const { id = '', applicationId = '' } = useParams();
  const profile = useRemote<ApplicantProfile>('/company/offers/' + encoded(id) + '/applications/' + encoded(applicationId) + '/profile');
  return <>
    <span className="eyebrow">POSTULACIÓN RECIBIDA</span><h2>Perfil del postulante</h2>
    <Notice {...profile} />
    {profile.data && <><dl><dt>Nombre</dt><dd>{profile.data.nombre} {profile.data.apellido}</dd>
      <dt>Correo</dt><dd>{profile.data.correo}</dd><dt>Especializaciones</dt>
      <dd>{profile.data.especializaciones.join(', ') || 'Sin cargar'}</dd></dl>
      <a className="button-link" href={'/api/company/offers/' + encoded(id) + '/applications/' + encoded(applicationId) + '/curriculum'}>Descargar CV de esta postulación</a></>}
  </>;
}
