export function authToken(): string {
  const stored = localStorage.getItem('pics2text.token');
  if (stored) return stored;
  return import.meta.env.DEV ? (import.meta.env.VITE_DEV_TOKEN ?? '') : '';
}
