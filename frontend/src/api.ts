export type Account = {
  id: string; correo: string; nombre: string; apellido: string;
  tipoCuenta: 'TALENTO' | 'STAFF'; rol: 'TALENTO' | 'ADMIN' | 'RECLUTADOR' | 'EDITOR';
  empresaId: string | null; empresaNombre: string | null;
};

export type TalentProfile = {
  nombre: string;
  apellido: string;
  correo: string;
  telefono: string | null;
  especializaciones: string[];
  especializacionesDisponibles?: string[];
  notificarNovedadesCursos: boolean;
  notificarOfertas: boolean;
  progresoCursos: { nombre: string; porcentajeAvance: number; estado: string }[];
  proyectosCompletados: { nombre: string; fechaCompletado: string }[];
  logros: { titulo: string; origen: string }[];
};

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, { ...init, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(response.status, error.message ?? 'No se pudo completar la solicitud. Intentá nuevamente.');
  }
  return response.status === 204 ? undefined as T : response.json();
}

async function withCsrf<T>(method: string, path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  const csrf = await api<{ token: string; headerName: string }>('/auth/csrf');
  return api<T>(path, {
    method,
    body,
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': contentType },
  });
}

export async function post<T>(path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  return withCsrf('POST', path, body, contentType);
}

export async function patch<T>(path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  return withCsrf('PATCH', path, body, contentType);
}
