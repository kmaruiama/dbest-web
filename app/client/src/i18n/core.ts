import { LANGS, strings, type Lang } from "./strings";

export type Params = Record<string, string | number>;

export const DEFAULT_LANG: Lang = "pt-BR";

const STORAGE_KEY = "dbest.lang";

export function isLang(value: string): value is Lang {
  return (LANGS as readonly string[]).includes(value);
}

export function initialLang(): Lang {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored !== null && isLang(stored) ? stored : DEFAULT_LANG;
}

export function applyLang(lang: Lang): void {
  document.documentElement.lang = lang;
  localStorage.setItem(STORAGE_KEY, lang);
}

export function label(lang: Lang, key: string, fallback: string): string {
  return strings[key] === undefined ? fallback : translate(lang, key);
}

export function humanize(identifier: string): string {
  const spaced = identifier
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ");
  return spaced.toLowerCase();
}

export function translate(lang: Lang, key: string, params?: Params): string {
  const entry = strings[key];
  if (entry === undefined) return key;
  let text = entry[lang];
  if (params === undefined) return text;
  for (const [name, value] of Object.entries(params)) {
    text = text.replaceAll(`{${name}}`, String(value));
  }
  return text;
}
