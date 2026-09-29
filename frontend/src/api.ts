export type Account = {
  id: string; correo: string; nombre: string; apellido: string;
  tipoCuenta: 'TALENTO' | 'STAFF'; rol: 'TALENTO' | 'ADMIN' | 'RECLUTADOR' | 'EDITOR';
  empresaId: string | null; empresaNombre: string | null;
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

export async function post<T>(path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  // Obtain a fresh token for each operation: login/logout rotate the session token.
  const csrf = await api<{ token: string; headerName: string }>('/auth/csrf');
  return api<T>(path, { method: 'POST', body,
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': contentType } });
}
