import { StrictMode, createContext, useContext, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, post, ApiError, type Account } from './api';
import './styles.css';

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

function App() {
  const auth = useContext(Auth);
  return <>
    <header><Link to="/" className="brand" aria-label="XPerience, inicio"><span className="brand-mark">X</span>XPerience</Link><span className="tagline">Tu próximo paso empieza acá.</span></header>
    <main><aside><span className="eyebrow">FORMACIÓN + OPORTUNIDADES</span><h1>Convertí lo que sabés<br />en tu próxima oportunidad.</h1><p>Un espacio para aprender, demostrar tus habilidades y conectar con el mundo IT.</p><div className="journey"><span>01 · Aprendé</span><span>02 · Creá</span><span>03 · Conectá</span></div></aside>
      <section className="panel" aria-label="Acceso a XPerience">
        {auth.loading ? <p role="status">Cargando…</p> : auth.error ? <><p role="alert">{auth.error}</p><button onClick={() => void auth.refresh().catch(() => {})}>Reintentar</button></> :
        <Routes>
          <Route path="/" element={<Navigate to={auth.account ? '/mi-cuenta' : '/ingresar'} replace />} />
          <Route path="/ingresar" element={auth.account ? <Navigate to="/mi-cuenta" replace /> : <AccessForm />} />
          <Route path="/registro" element={auth.account ? <Navigate to="/mi-cuenta" replace /> : <AccessForm register />} />
          <Route path="/mi-cuenta" element={auth.account ? <MyAccount account={auth.account} /> : <Navigate to="/ingresar" replace />} />
          <Route path="*" element={<><h2>Página no encontrada</h2><Link to="/">Volver al inicio</Link></>} />
        </Routes>}
      </section>
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
    <button disabled={busy} onClick={verify}>Verificar acceso</button><button disabled={busy} className="secondary" onClick={logout}>Cerrar sesión</button>
  </>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter></StrictMode>);
