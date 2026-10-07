export function readPreference<T>(
  key: string,
  fallback: T,
  decode: (stored: string) => T,
): T {
  const stored = localStorage.getItem(key);
  return stored === null ? fallback : decode(stored);
}

export function writePreference(key: string, value: string): void {
  localStorage.setItem(key, value);
}
