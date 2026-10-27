import { createContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError, type Account } from './api';

export type AuthState = { account: Account | null; loading: boolean; error: string; refresh: () => Promise<void>; clear: () => void };
export const Auth = createContext<AuthState>(null!);

export function AuthProvider({ children }: { children: ReactNode }) {
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
