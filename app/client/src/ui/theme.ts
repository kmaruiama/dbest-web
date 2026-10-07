import { readPreference, writePreference } from "./preferences";

export const THEMES = ["light", "rose", "dark", "midnight"] as const;

export type Theme = (typeof THEMES)[number];

const STORAGE_KEY = "dbest.theme";

export function isTheme(value: unknown): value is Theme {
  return (
    typeof value === "string" && (THEMES as readonly string[]).includes(value)
  );
}

export function initialTheme(): Theme {
  return readPreference(STORAGE_KEY, "light", (stored) =>
    isTheme(stored) ? stored : "light",
  );
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  if (theme === "light") {
    root.removeAttribute("data-theme");
  } else {
    root.dataset.theme = theme;
  }
  writePreference(STORAGE_KEY, theme);
}
