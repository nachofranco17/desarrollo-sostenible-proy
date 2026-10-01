import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, patch, post } from './api';

type Role = 'ADMIN' | 'RECLUTADOR' | 'EDITOR';
type Member = { usuarioId: string; empresaId: string; correo: string; nombre: string; apellido: string; rol: Role | null; estado: string };
const roleNames: Record<Role, string> = { ADMIN: 'Administrador', RECLUTADOR: 'Reclutador', EDITOR: 'Editor de contenido' };

export function StaffPage({ currentUserId }: { currentUserId: string }) {
  const [members, setMembers] = useState<Member[]>([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  async function reload() { setMembers(await api<Member[]>('/staff')); setLoaded(true); }
  useEffect(() => { void reload().catch(e => setError(e.message)); }, []);
  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget;
    const correo = new FormData(form).get('correo');
    setBusy(true); setError(''); setMessage('');
    try {
      const result = await post<{ message: string }>('/staff/invitations', JSON.stringify({ correo }));
      setMessage(result.message); form.reset(); await reload();
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo enviar la invitación.'); }
    finally { setBusy(false); }
  }
  async function assign(event: FormEvent<HTMLFormElement>, member: Member) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    setBusy(true); setError(''); setMessage('');
    try {
      await post('/auth/reauthenticate', JSON.stringify({ password: data.get('password') }));
      await patch(`/staff/${member.usuarioId}/rol`, JSON.stringify({ rol: data.get('rol') }));
      setMessage('Rol actualizado. Los permisos ya están vigentes.'); await reload();
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo cambiar el rol.'); }
    finally { form.reset(); setBusy(false); }
  }
  return <>
    <Link to="/mi-cuenta">Volver a mi cuenta</Link><h2>Staff de la empresa</h2>
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    <form onSubmit={invite}><h3>Invitar un miembro</h3>
      <label>Correo del invitado<input name="correo" type="email" required maxLength={254} /></label>
      <p>La invitación vence en 48 horas. Una vez aceptada, asignale un rol para habilitar su acceso.</p>
      <button disabled={busy}>Enviar invitación</button>
    </form>
    <h3>Miembros</h3>{!loaded && !error && <p role="status">Cargando…</p>}
    <button className="secondary" disabled={busy} onClick={() => void reload().catch(e => setError(e.message))}>Actualizar miembros</button>
    {members.map(member => <section key={member.usuarioId} className="staff-member">
      <h3>{member.nombre ? `${member.nombre} ${member.apellido}` : 'Invitación pendiente'}{member.usuarioId === currentUserId ? ' (vos)' : ''}</h3>
      <p>{member.correo}</p>
      <p>{member.estado === 'PENDIENTE' ? 'Invitación pendiente' : member.estado === 'ACTIVA' ? 'Activo' : 'Baja'} · {member.rol ? roleNames[member.rol] : 'Sin rol'}</p>
      {member.estado === 'ACTIVA' && member.usuarioId !== currentUserId && <form onSubmit={e => void assign(e, member)}>
        <label htmlFor={`rol-${member.usuarioId}`}>Rol</label>
        <select id={`rol-${member.usuarioId}`} name="rol" defaultValue={member.rol ?? 'RECLUTADOR'}>
          {Object.entries(roleNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        <label>Tu contraseña de administrador<input name="password" type="password" autoComplete="current-password" required maxLength={128} /></label>
        <button disabled={busy}>Asignar rol</button>
      </form>}
    </section>)}
  </>;
}

export function AcceptInvitation() {
  // Fragment stays out of HTTP URLs, access logs and referrers; clear it from the address bar.
  const [token] = useState(() => window.location.hash.slice(1));
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => { window.history.replaceState(null, '', window.location.pathname); }, []);
  async function accept(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    setBusy(true); setError('');
    try {
      const csrf = await api<{ token: string; headerName: string }>('/auth/csrf');
      await api('/invitations/accept', { method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'X-Invitation-Token': token },
        body: JSON.stringify({ nombre: data.get('nombre'), apellido: data.get('apellido'), password: data.get('password') }),
      });
      form.reset(); setDone(true);
    } catch { setError('No se pudo aceptar la invitación. Revisá los datos; si el enlace venció o ya fue usado, pedí otra invitación.'); }
    finally { setBusy(false); }
  }
  return <><h2>Aceptar invitación</h2>{done ? <>
    <p role="status">Invitación aceptada. Un administrador debe asignarte un rol antes de que puedas iniciar sesión.</p>
    <Link to="/ingresar">Ir a iniciar sesión</Link>
  </> : !token ? <p role="alert">Abrí el enlace que recibiste en la invitación.</p> : <form onSubmit={accept}>
    {error && <p role="alert">{error}</p>}
    <label>Nombre<input name="nombre" required maxLength={80} autoComplete="given-name" /></label>
    <label>Apellido<input name="apellido" required maxLength={80} autoComplete="family-name" /></label>
    <label>Contraseña<input name="password" type="password" required minLength={12} maxLength={128} autoComplete="new-password" /></label>
    <button disabled={busy}>Aceptar invitación</button>
  </form>}</>;
}
