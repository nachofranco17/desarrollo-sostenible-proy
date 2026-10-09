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
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  // Con FormData el navegador arma el Content-Type con el boundary del multipart.
  if (!(body instanceof FormData)) headers['Content-Type'] = contentType;
  return api<T>(path, { method, body, headers });
}

export async function post<T>(path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  return withCsrf('POST', path, body, contentType);
}

export async function patch<T>(path: string, body?: BodyInit, contentType = 'application/json'): Promise<T> {
  return withCsrf('PATCH', path, body, contentType);
}

export async function put<T>(path: string, body?: BodyInit): Promise<T> {
  return withCsrf('PUT', path, body);
}

export async function del<T>(path: string): Promise<T> {
  return withCsrf('DELETE', path);
}

export async function upload<T>(path: string, form: FormData): Promise<T> {
  return withCsrf('POST', path, form);
}

// ---- RF5: gestión de cursos y proyectos ----

export type CourseType = 'CURSO' | 'PROYECTO';
export type CourseLevel = 'INICIAL' | 'INTERMEDIO' | 'AVANZADO';
export type CourseState = 'BORRADOR' | 'PUBLICADO' | 'BAJADO';

/** Opciones de tecnología para catálogo (RF3) y alta de cursos (RF5). */
export const TECHNOLOGY_OPTIONS = [
  'Java', 'Spring', 'React', 'TypeScript', 'JavaScript', 'SQL', 'Python',
  'Node.js', 'Docker', 'Kotlin', 'Go', 'Angular', '.NET', 'AWS',
] as const;

// ---- RF3: catálogo público ----

export type CatalogItem = {
  id: string; tipo: CourseType; titulo: string; tecnologia: string; nivel: CourseLevel;
  duracionHoras: number; costo: number; empresaNombre: string;
};

export type CatalogDetail = CatalogItem & { descripcion: string };

export type CatalogPage = { items: CatalogItem[]; tecnologiasDisponibles: string[] };

export type CatalogFilters = {
  q?: string; tecnologia?: string; nivel?: CourseLevel | '';
  duracionMin?: string; duracionMax?: string; costoMin?: string; costoMax?: string;
};

export function catalogQuery(filters: CatalogFilters): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    const trimmed = value?.trim();
    if (trimmed) params.set(key, trimmed);
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

// ---- RF4: inscripciones del Talento ----

export type EnrollmentItem = {
  id: string;
  cursoId: string;
  tipo: CourseType;
  titulo: string;
  porcentajeAvance: number;
  fecha: string;
};

export type EnrollmentPage = { items: EnrollmentItem[] };

export type CourseData = {
  titulo: string; descripcion: string; tecnologia: string;
  nivel: CourseLevel; duracionHoras: number; costo: number;
};

export type CourseSummary = {
  id: string; tipo: CourseType; titulo: string; tecnologia: string; nivel: CourseLevel;
  estado: CourseState; cantidadElementos: number; actualizadoEn: string;
};

export type Lesson = { id: string; numero: number; titulo: string; cuerpo: string };
export type Deliverable = {
  id: string; numero: number; titulo: string; consigna: string; pista: string;
  cantidadRespuestasAceptadas: number;
};
export type CourseMaterial = { id: string; nombre: string; tipoMime: string; tamanioBytes: number; subidoEn: string };

export type CourseDetail = CourseData & {
  id: string; tipo: CourseType; estado: CourseState; creadoEn: string; actualizadoEn: string;
  lecciones: Lesson[]; entregables: Deliverable[]; materiales: CourseMaterial[];
};
