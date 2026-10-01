export function authToken(): string {
  return localStorage.getItem('pics2text.token') ?? '';
}

export interface AuthenticatedUser {
  id: string;
  username: string;
  displayName: string;
  globalRole: string;
}

export interface LoginSession {
  tokenName: string;
  tokenValue: string;
  tokenTimeout: number;
  activeTimeout: number;
  user: AuthenticatedUser;
}

export async function login(username: string, password: string): Promise<LoginSession> {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `登录失败：${response.status}`);
  }
  const session = await response.json() as LoginSession;
  localStorage.setItem('pics2text.token', session.tokenValue);
  localStorage.setItem('pics2text.user', JSON.stringify(session.user));
  return session;
}

export async function register(
  username: string,
  password: string,
  displayName: string,
): Promise<LoginSession> {
  const response = await fetch('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, displayName }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `注册失败：${response.status}`);
  }
  return login(username, password);
}

export async function logout(): Promise<void> {
  const token = authToken();
  try {
    if (token) await fetch('/api/v1/auth/logout', {
      method: 'POST', headers: { Authorization: `Bearer ${token}` },
    });
  } finally {
    clearSession();
    redirectToLogin('logout');
  }
}

export function clearSession(): void {
  localStorage.removeItem('pics2text.token');
  localStorage.removeItem('pics2text.user');
}

export function redirectToLogin(reason = 'expired'): void {
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  const url = new URL(window.location.href);
  if (url.searchParams.get('view') === 'login') return;
  window.location.assign(`/?view=login&reason=${reason}&returnTo=${encodeURIComponent(current)}`);
}

export function safeReturnTo(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/';
  try {
    const target = new URL(value, window.location.origin);
    return target.origin === window.location.origin
      ? `${target.pathname}${target.search}${target.hash}` : '/';
  } catch {
    return '/';
  }
}
