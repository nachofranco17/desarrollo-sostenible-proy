import { StrictMode, createContext, useContext, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api, post, patch, del, ApiError, type Account, type TalentProfile } from './api';
import { CourseEditor, CourseList, NewCourse } from './gestion';
import { CatalogList, CatalogDetailPage } from './catalogo';
import { EnrollmentsPage } from './inscripciones';
import './styles.css';
import { StaffPage, AcceptInvitation } from './staff';

type AuthState = { account: Account | null; loading: boolean; error: string; refresh: () => Promise<void>; clear: () => void };
const Auth = createContext<AuthState>(null!);

function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  async function refresh() {
    try {
      setAccount(await api<Account>('/account'));
      setError('');
    } catch (failure) {
      setAccount(null);
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) setError('');
      else { setError('No pudimos conectar con el servidor.'); throw failure; }
    } finally { setLoading(false); }
  }
  useEffect(() => {
    const reload = () => { void refresh().catch(() => {}); };
    reload();
    window.addEventListener('focus', reload);
    return () => window.removeEventListener('focus', reload);
  }, []);
  return <Auth.Provider value={{ account, loading, error, refresh, clear: () => setAccount(null) }}>{children}</Auth.Provider>;
}

// Solo decide qué mostrar; el backend vuelve a verificar el permiso en cada solicitud (R9).
const canManageCourses = (account: Account | null) => account?.rol === 'ADMIN' || account?.rol === 'EDITOR';

function backLinkFor(path: string): { to: string; label: string } | null {
  if (path === '/catalogo') return { to: '/', label: 'Volver al inicio' };
  if (path.startsWith('/catalogo/')) return { to: '/catalogo', label: 'Volver al catálogo' };
  if (path === '/inscripciones' || path === '/perfil' || path === '/staff' || path === '/gestion') {
    return { to: '/mi-cuenta', label: 'Volver a mi cuenta' };
  }
  if (path === '/gestion/nuevo' || path.startsWith('/gestion/')) {
    return { to: '/gestion', label: 'Volver a cursos y proyectos' };
  }
  if (path === '/invitacion') return { to: '/ingresar', label: 'Volver al inicio' };
  if (path === '/mi-cuenta' || path === '/ingresar' || path === '/registro' || path === '/') return null;
  return { to: '/', label: 'Volver al inicio' };
}

function App() {
  const auth = useContext(Auth);
  const path = useLocation().pathname;
  const wide = path.startsWith('/gestion') || path.startsWith('/catalogo') || path.startsWith('/inscripciones');
  const back = backLinkFor(path);
  const courses = (page: ReactNode) => canManageCourses(auth.account) ? page : <Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />;
  return <>
    <header><Link to="/" className="brand" aria-label="XPerience, inicio"><span className="brand-mark">X</span>XPerience</Link><span className="tagline">Tu próximo paso empieza acá.</span></header>
    <main className={wide ? 'wide' : undefined}>{!wide && <aside><span className="eyebrow">FORMACIÓN + OPORTUNIDADES</span><h1>Convertí lo que sabés<br />en tu próxima oportunidad.</h1><p>Un espacio para aprender, demostrar tus habilidades y conectar con el mundo IT.</p><div className="journey"><span>01 · Aprendé</span><span>02 · Creá</span><span>03 · Conectá</span></div></aside>}
      <div className="panel-stack">
        {back && <p className="back-nav"><Link to={back.to}>← {back.label}</Link></p>}
        <section className="panel" aria-label="Acceso a XPerience">
          {auth.loading ? <p role="status">Cargando…</p> : auth.error ? <><p role="alert">{auth.error}</p><button onClick={() => void auth.refresh().catch(() => {})}>Reintentar</button></> :
          <Routes>
            <Route path="/" element={<Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />} />
            <Route path="/ingresar" element={auth.account ? <Navigate to="/mi-cuenta" replace /> : <AccessForm />} />
            <Route path="/registro" element={auth.account ? <Navigate to="/mi-cuenta" replace /> : <AccessForm register />} />
            <Route path="/catalogo" element={<CatalogList />} />
            <Route path="/catalogo/:id" element={<CatalogDetailPage account={auth.account} />} />
            <Route path="/inscripciones" element={auth.account?.rol === 'TALENTO' ? <EnrollmentsPage /> : <Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />} />
            <Route path="/mi-cuenta" element={auth.account ? <MyAccount account={auth.account} /> : <Navigate to="/ingresar" replace />} />
            <Route path="/perfil" element={auth.account?.rol === 'TALENTO' ? <TalentProfilePage /> : <Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />} />
            <Route path="/gestion" element={courses(<CourseList />)} />
            <Route path="/invitacion" element={<AcceptInvitation />} />
            <Route path="/staff" element={auth.account?.rol === 'ADMIN' ? <StaffPage currentUserId={auth.account.id} /> : <Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />} />
            <Route path="/gestion/nuevo" element={courses(<NewCourse />)} />
            <Route path="/gestion/:id" element={courses(<CourseEditor />)} />
            <Route path="*" element={<><h2>Página no encontrada</h2><p>No encontramos esa ruta.</p></>} />
          </Routes>}
        </section>
      </div>
    </main><footer>XPerience · Plataforma de formación y empleabilidad</footer>
  </>;
}

function AccessForm({ register = false }: { register?: boolean }) {
  const auth = useContext(Auth);
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => { setError(''); setMessage(''); }, [register]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true); setError(''); setMessage('');
    try {
      if (register) {
        const result = await post<{ message: string }>('/auth/register', JSON.stringify({
          nombre: data.get('nombre'), apellido: data.get('apellido'),
          correo: data.get('correo'), password: data.get('password'),
        }));
        setMessage(result.message);
        form.reset();
      } else {
        const body = new URLSearchParams({ correo: String(data.get('correo')), password: String(data.get('password')) });
        await post('/auth/login', body, 'application/x-www-form-urlencoded');
        await auth.refresh();
        navigate('/mi-cuenta', { replace: true });
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'No se pudo completar la solicitud.');
    } finally { setBusy(false); }
  }
  return <div key={register ? 'register' : 'login'}>
    <span className="eyebrow">{register ? 'EMPEZÁ TU CAMINO' : 'QUÉ BUENO VERTE'}</span>
    <h2>{register ? 'Creá tu cuenta de Talento' : 'Iniciá sesión'}</h2>
    <p className="muted">{register ? 'Registrate para dar tu próximo paso en IT.' : 'Ingresá con el correo y la contraseña de tu cuenta.'}</p>
    <form onSubmit={submit}>
      {register && <div className="name-fields"><label>Nombre<input name="nombre" autoComplete="given-name" required maxLength={80} /></label><label>Apellido<input name="apellido" autoComplete="family-name" required maxLength={80} /></label></div>}
      <label>Correo electrónico<input name="correo" type="email" autoComplete="username" required maxLength={254} /></label>
      <label>Contraseña<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 12 : undefined} maxLength={128} aria-describedby={register ? 'password-help' : undefined} /></label>
      {register && <small id="password-help">Usá entre 12 y 128 caracteres. Podés usar una frase.</small>}
      {error && <p className="notice error" role="alert">{error}</p>}
      {message && <p className="notice" role="status">{message}</p>}
      <button type="submit" disabled={busy}>{busy ? 'Procesando…' : register ? 'Crear cuenta de Talento' : 'Ingresar'}</button>
    </form>
    <p className="switch">{register ? '¿Ya tenés una cuenta? ' : '¿Es tu primera vez? '}<Link to={register ? '/ingresar' : '/registro'}>{register ? 'Iniciá sesión' : 'Registrate como Talento'}</Link></p>
    <p className="switch"><Link to="/catalogo">Ver catálogo de cursos y proyectos</Link></p>
    <p className="staff-note">Si sos parte de una empresa, ingresá con tu cuenta habilitada. El resto del staff se incorpora por invitación.</p>
  </div>;
}

const roles = { TALENTO: 'Talento', ADMIN: 'Administrador', RECLUTADOR: 'Reclutador', EDITOR: 'Editor de contenido' };
function MyAccount({ account }: { account: Account }) {
  const auth = useContext(Auth);
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(false);
  async function deleteAccount() {
    setBusy(true); setError('');
    try {
      await del('/account');
      auth.clear(); navigate('/ingresar', { replace: true });
    } catch { setError('No pudimos eliminar tu cuenta. Intentá nuevamente.'); }
    finally { setBusy(false); }
  }
  async function logout() {
    setBusy(true); setError('');
    try {
      await post('/auth/logout');
      auth.clear(); navigate('/ingresar', { replace: true });
    } catch { setError('No pudimos cerrar la sesión. Intentá nuevamente.'); }
    finally { setBusy(false); }
  }
  async function verify() {
    setBusy(true); setError('');
    try { await auth.refresh(); setMessage('Acceso verificado con el servidor.'); }
    catch { setError('No pudimos verificar tu acceso.'); }
    finally { setBusy(false); }
  }
  return <>
    <span className="badge">Sesión iniciada</span><h2>Hola, {account.nombre}</h2><p className="muted">Esta es tu cuenta en XPerience.</p>
    <dl><dt>Nombre</dt><dd>{account.nombre} {account.apellido}</dd><dt>Correo</dt><dd>{account.correo}</dd><dt>Rol</dt><dd>{roles[account.rol]}</dd>{account.empresaNombre && <><dt>Empresa</dt><dd>{account.empresaNombre}</dd></>}</dl>
    {message && <p className="notice" role="status">{message}</p>}{error && <p className="notice error" role="alert">{error}</p>}
    {account.rol === 'TALENTO' && <Link className="button-link" to="/perfil">Ver mi perfil</Link>}
    {account.rol === 'TALENTO' && <Link className="button-link" to="/inscripciones">Mis inscripciones</Link>}
    <Link className="button-link" to="/catalogo">Ver catálogo</Link>
    {canManageCourses(account) && <Link className="button-link" to="/gestion">Gestionar cursos y proyectos</Link>}
    {account.rol === 'ADMIN' && <Link className="button-link" to="/staff">Gestionar staff</Link>}
    <button disabled={busy} onClick={verify}>Verificar acceso</button><button disabled={busy} className="secondary" onClick={logout}>Cerrar sesión</button>
    {deleting ? <section role="dialog" aria-labelledby="delete-account-title">
      <h3 id="delete-account-title">Eliminar mi cuenta</h3>
      <p>Perderás el acceso a tu cuenta en todos los dispositivos. Esta acción no se puede deshacer desde la aplicación.</p>
      <button disabled={busy} onClick={() => void deleteAccount()}>Confirmar eliminación</button>
      <button disabled={busy} onClick={() => setDeleting(false)}>Cancelar</button>
    </section> : <button disabled={busy} className="secondary" onClick={() => setDeleting(true)}>Eliminar mi cuenta</button>}
  </>;
}

const FALLBACK_SPECIALIZATIONS = [
  'Backend', 'Frontend', 'Full Stack', 'React', 'Seguridad', 'Datos', 'DevOps',
  'Mobile', 'UX/UI', 'Testing', 'Cloud', 'Gestión de proyectos',
  'Inteligencia Artificial',
];

const PHONE_COUNTRY_CODES = [
  { code: '+598', label: 'UY +598' },
  { code: '+54', label: 'AR +54' },
  { code: '+55', label: 'BR +55' },
  { code: '+56', label: 'CL +56' },
  { code: '+57', label: 'CO +57' },
  { code: '+51', label: 'PE +51' },
  { code: '+52', label: 'MX +52' },
  { code: '+34', label: 'ES +34' },
  { code: '+1', label: 'US/CA +1' },
];

function parseStoredPhone(raw: string | null | undefined): { code: string; number: string } {
  const value = (raw ?? '').trim();
  if (!value) return { code: '+598', number: '' };
  const sorted = [...PHONE_COUNTRY_CODES].sort((a, b) => b.code.length - a.code.length);
  for (const option of sorted) {
    if (value.startsWith(option.code)) {
      return { code: option.code, number: value.slice(option.code.length).replace(/\D/g, '') };
    }
  }
  return { code: '+598', number: value.replace(/\D/g, '') };
}

function composePhone(code: string, number: string): string | null {
  const digits = number.replace(/\D/g, '');
  if (!digits) return null;
  return `${code} ${digits}`;
}

function TalentProfilePage() {
  const auth = useContext(Auth);
  const [profile, setProfile] = useState<TalentProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [correo, setCorreo] = useState('');
  const [phoneCode, setPhoneCode] = useState('+598');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [especializaciones, setEspecializaciones] = useState<string[]>([]);
  const [catalogo, setCatalogo] = useState<string[]>(FALLBACK_SPECIALIZATIONS);
  const [notificarNovedadesCursos, setNotificarNovedadesCursos] = useState(true);
  const [notificarOfertas, setNotificarOfertas] = useState(true);
  const [passwordActual, setPasswordActual] = useState('');
  const [passwordNueva, setPasswordNueva] = useState('');

  function applyProfile(data: TalentProfile) {
    setProfile(data);
    setNombre(data.nombre);
    setApellido(data.apellido);
    setCorreo(data.correo);
    const phone = parseStoredPhone(data.telefono);
    setPhoneCode(phone.code);
    setPhoneNumber(phone.number);
    const available = (data.especializacionesDisponibles?.length
      ? data.especializacionesDisponibles
      : FALLBACK_SPECIALIZATIONS);
    setCatalogo(available);
    // Solo especializaciones del catálogo: lo guardado fuera del listado no se muestra ni se reenvía.
    setEspecializaciones((data.especializaciones ?? []).filter((item) => available.includes(item)));
    setNotificarNovedadesCursos(data.notificarNovedadesCursos);
    setNotificarOfertas(data.notificarOfertas);
    setPasswordActual('');
    setPasswordNueva('');
  }

  function sanitizePersonName(value: string): string {
    // Solo letras (con tilde) y espacios: sin números ni símbolos.
    return value.replace(/[^\p{L}\p{M} ]+/gu, '');
  }

  function toggleEspecializacion(value: string) {
    if (!catalogo.includes(value)) return;
    setEspecializaciones((current) =>
      current.includes(value) ? current.filter((item) => item !== value) : [...current, value]);
  }

  function validateClient(): string | null {
    const namePattern = /^[\p{L}](?:[\p{L}\p{M} ]*[\p{L}\p{M}])?$/u;
    const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    const cleanNombre = nombre.trim().replace(/\s+/g, ' ');
    const cleanApellido = apellido.trim().replace(/\s+/g, ' ');
    const cleanCorreo = correo.trim().toLowerCase();
    const composedPhone = composePhone(phoneCode, phoneNumber);
    if (cleanNombre.length < 2 || cleanNombre.length > 80 || !namePattern.test(cleanNombre)) {
      return 'El nombre solo puede tener letras y espacios (sin números ni símbolos).';
    }
    if (cleanApellido.length < 2 || cleanApellido.length > 80 || !namePattern.test(cleanApellido)) {
      return 'El apellido solo puede tener letras y espacios (sin números ni símbolos).';
    }
    if (!emailPattern.test(cleanCorreo) || cleanCorreo.length > 254) {
      return 'Revisá el correo electrónico.';
    }
    if (composedPhone) {
      const digits = [...composedPhone].filter((ch) => ch >= '0' && ch <= '9').length;
      if (composedPhone.length > 30 || digits < 7 || phoneNumber.replace(/\D/g, '').length < 6) {
        return 'Revisá el teléfono: elegí el código de país y un número válido.';
      }
    }
    if (especializaciones.some((item) => !catalogo.includes(item))) {
      return 'Elegí especializaciones únicamente del listado disponible.';
    }
    if ((passwordActual && !passwordNueva) || (!passwordActual && passwordNueva)) {
      return 'Para cambiar la contraseña completá la actual y la nueva.';
    }
    if (passwordNueva && (passwordNueva.length < 12 || passwordNueva.length > 128)) {
      return 'La nueva contraseña debe tener entre 12 y 128 caracteres.';
    }
    return null;
  }

  async function load() {
    setLoading(true); setError('');
    try {
      applyProfile(await api<TalentProfile>('/profile/me'));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'No pudimos cargar el perfil.');
      setProfile(null);
    } finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(''); setMessage('');
    const clientError = validateClient();
    if (clientError) {
      setError(clientError);
      setBusy(false);
      return;
    }
    try {
      const payload: Record<string, unknown> = {
        nombre: nombre.trim().replace(/\s+/g, ' '),
        apellido: apellido.trim().replace(/\s+/g, ' '),
        correo: correo.trim(),
        telefono: composePhone(phoneCode, phoneNumber),
        especializaciones,
        notificarNovedadesCursos,
        notificarOfertas,
      };
      if (passwordActual || passwordNueva) {
        payload.passwordActual = passwordActual;
        payload.passwordNueva = passwordNueva;
      }
      const updated = await patch<TalentProfile>('/profile/me', JSON.stringify(payload));
      applyProfile(updated);
      await auth.refresh().catch(() => {});
      setEditing(false);
      setMessage('Perfil actualizado.');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'No se pudo guardar el perfil.');
    } finally { setBusy(false); }
  }

  if (loading) return <p role="status">Cargando perfil…</p>;
  if (!profile) return <p className="notice error" role="alert">{error || 'No pudimos cargar el perfil.'}</p>;

  return <>
    <span className="eyebrow">PERFIL DEL TALENTO</span>
    <h2>Mi perfil</h2>
    <p className="muted">Datos personales, contacto, especializaciones y configuración de tu cuenta.</p>
    {message && <p className="notice" role="status">{message}</p>}
    {error && <p className="notice error" role="alert">{error}</p>}

    {!editing ? <>
      <section className="profile-section" aria-label="Datos personales y contacto">
        <h3>Datos personales y contacto</h3>
        <dl>
          <dt>Nombre</dt><dd>{profile.nombre} {profile.apellido}</dd>
          <dt>Correo</dt><dd>{profile.correo}</dd>
          <dt>Teléfono</dt><dd>{profile.telefono || <span className="empty-hint">Sin cargar</span>}</dd>
          <dt>Especializaciones</dt>
          <dd>{(() => {
            const available = profile.especializacionesDisponibles?.length
              ? profile.especializacionesDisponibles
              : FALLBACK_SPECIALIZATIONS;
            const visible = profile.especializaciones.filter((item) => available.includes(item));
            return visible.length
              ? <ul className="chip-list">{visible.map((item) => <li key={item} className="chip">{item}</li>)}</ul>
              : <span className="empty-hint">Sin cargar</span>;
          })()}</dd>
        </dl>
      </section>
      <section className="profile-section" aria-label="Preferencias de notificaciones">
        <h3>Preferencias de notificaciones</h3>
        <dl>
          <dt>Novedades de cursos</dt><dd>{profile.notificarNovedadesCursos ? 'Activadas' : 'Desactivadas'}</dd>
          <dt>Ofertas laborales</dt><dd>{profile.notificarOfertas ? 'Activadas' : 'Desactivadas'}</dd>
        </dl>
      </section>
      <section className="profile-section" aria-label="Progreso de cursos">
        <h3>Progreso de cursos</h3>
        {profile.progresoCursos.length === 0
          ? <p className="empty-hint">Todavía no hay cursos con progreso para mostrar.</p>
          : <ul>{profile.progresoCursos.map((item) => <li key={item.nombre}>{item.nombre}: {item.porcentajeAvance}% ({item.estado})</li>)}</ul>}
      </section>
      <section className="profile-section" aria-label="Proyectos completados">
        <h3>Proyectos completados</h3>
        {profile.proyectosCompletados.length === 0
          ? <p className="empty-hint">Todavía no hay proyectos completados para mostrar.</p>
          : <ul>{profile.proyectosCompletados.map((item) => <li key={item.nombre}>{item.nombre} · {item.fechaCompletado}</li>)}</ul>}
      </section>
      <section className="profile-section" aria-label="Logros">
        <h3>Logros</h3>
        {profile.logros.length === 0
          ? <p className="empty-hint">Los logros se mostrarán cuando completes cursos o proyectos.</p>
          : <ul>{profile.logros.map((item) => <li key={item.titulo}>{item.titulo} · {item.origen}</li>)}</ul>}
      </section>
      <button type="button" onClick={() => { setEditing(true); setMessage(''); setError(''); }}>Editar perfil</button>
    </> : <form onSubmit={save}>
      <section className="profile-section" aria-label="Editar datos personales">
        <h3>Datos personales y contacto</h3>
        <div className="profile-fields">
          <div className="name-fields">
            <label>Nombre<input value={nombre} onChange={(e) => setNombre(sanitizePersonName(e.target.value))} required maxLength={80} autoComplete="given-name" /></label>
            <label>Apellido<input value={apellido} onChange={(e) => setApellido(sanitizePersonName(e.target.value))} required maxLength={80} autoComplete="family-name" /></label>
          </div>
          <label>Correo electrónico<input type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} required maxLength={254} /></label>
          <label>Teléfono
            <div className="phone-fields">
              <select
                aria-label="Código de país"
                value={phoneCode}
                onChange={(e) => setPhoneCode(e.target.value)}
              >
                {PHONE_COUNTRY_CODES.map((option) => (
                  <option key={option.code} value={option.code}>{option.label}</option>
                ))}
              </select>
              <input
                type="text"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder="Número sin código"
                value={phoneNumber}
                maxLength={15}
                onChange={(e) => setPhoneNumber(e.target.value.replace(/\D/g, ''))}
                onKeyDown={(e) => {
                  if (e.key.length === 1 && !/[0-9]/.test(e.key) && !e.ctrlKey && !e.metaKey) {
                    e.preventDefault();
                  }
                }}
              />
            </div>
            <small>Solo números en el teléfono. El código de país se elige a la izquierda.</small>
          </label>
          <div className="chip-field">
            <span className="chip-field-label" id="especializaciones-label">Especializaciones</span>
            <div className="specialization-options" role="group" aria-labelledby="especializaciones-label">
              {catalogo.map((item) => {
                const selected = especializaciones.includes(item);
                return (
                  <button
                    key={item}
                    type="button"
                    className={selected ? 'chip chip-selected' : 'chip chip-option'}
                    aria-pressed={selected}
                    onClick={() => toggleEspecializacion(item)}
                  >
                    {item}{selected ? <span className="chip-remove" aria-hidden="true">×</span> : null}
                  </button>
                );
              })}
            </div>
            <small id="especializaciones-help">Elegí una o más opciones del catálogo. Tocá de nuevo para quitarla.</small>
          </div>
        </div>
      </section>
      <section className="profile-section" aria-label="Editar notificaciones">
        <h3>Preferencias de notificaciones</h3>
        <div className="preference-list">
          <label className="preference-row">
            <span className="preference-text">
              <span className="preference-title">Novedades de cursos</span>
              <span className="preference-desc">Recibir avisos cuando haya novedades en cursos</span>
            </span>
            <input type="checkbox" className="preference-input" checked={notificarNovedadesCursos} onChange={(e) => setNotificarNovedadesCursos(e.target.checked)} />
            <span className="preference-switch" aria-hidden="true" />
          </label>
          <label className="preference-row">
            <span className="preference-text">
              <span className="preference-title">Ofertas laborales</span>
              <span className="preference-desc">Recibir avisos de nuevas ofertas</span>
            </span>
            <input type="checkbox" className="preference-input" checked={notificarOfertas} onChange={(e) => setNotificarOfertas(e.target.checked)} />
            <span className="preference-switch" aria-hidden="true" />
          </label>
        </div>
      </section>
      <details className="collapse-block">
        <summary>Configuración de cuenta</summary>
        <div className="collapse-body">
          <p className="empty-hint section-note">Completá ambos campos solo si querés cambiar la contraseña.</p>
          <div className="profile-fields">
            <label>Contraseña actual<input type="password" value={passwordActual} onChange={(e) => setPasswordActual(e.target.value)} minLength={12} maxLength={128} autoComplete="current-password" /></label>
            <label>Nueva contraseña<input type="password" value={passwordNueva} onChange={(e) => setPasswordNueva(e.target.value)} minLength={12} maxLength={128} autoComplete="new-password" /></label>
          </div>
        </div>
      </details>
      <button type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar cambios'}</button>
      <button type="button" className="secondary" disabled={busy} onClick={() => { setEditing(false); applyProfile(profile); setError(''); setMessage(''); }}>Cancelar</button>
    </form>}
  </>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter></StrictMode>);
