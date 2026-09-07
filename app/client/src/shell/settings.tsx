import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { applyTheme, initialTheme, type Theme } from "./theme";

const PALETTE_VISIBLE_KEY = "dbest.palette.visible";
const ENGINE_CLASS_KEY = "dbest.caption.class";
const EXPRESSION_KEY = "dbest.caption.expression";

export type Settings = {
  theme: Theme;
  paletteVisible: boolean;
  showEngineClass: boolean;
  showExpression: boolean;
};

type Store = Settings & {
  update: (patch: Partial<Settings>) => void;
};

function storedFlag(key: string, fallback: boolean): boolean {
  const stored = localStorage.getItem(key);
  return stored === null ? fallback : stored === "true";
}

function initialSettings(): Settings {
  localStorage.removeItem("dbest.axis");
  return {
    theme: initialTheme(),
    paletteVisible: storedFlag(PALETTE_VISIBLE_KEY, true),
    showEngineClass: storedFlag(ENGINE_CLASS_KEY, false),
    showExpression: storedFlag(EXPRESSION_KEY, true),
  };
}

function persist(patch: Partial<Settings>): void {
  if (patch.theme !== undefined) applyTheme(patch.theme);
  if (patch.paletteVisible !== undefined) {
    localStorage.setItem(PALETTE_VISIBLE_KEY, String(patch.paletteVisible));
  }
  if (patch.showEngineClass !== undefined) {
    localStorage.setItem(ENGINE_CLASS_KEY, String(patch.showEngineClass));
  }
  if (patch.showExpression !== undefined) {
    localStorage.setItem(EXPRESSION_KEY, String(patch.showExpression));
  }
}

const SettingsContext = createContext<Store>({
  ...initialSettings(),
  update: () => undefined,
});

type Props = {
  children: ReactNode;
};

export function SettingsProvider({ children }: Props) {
  const [settings, setSettings] = useState<Settings>(initialSettings);
  const value = useMemo<Store>(
    () => ({
      ...settings,
      update: (patch: Partial<Settings>) => {
        persist(patch);
        setSettings((current) => ({ ...current, ...patch }));
      },
    }),
    [settings],
  );
  return (
    <SettingsContext.Provider value={value}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings(): Store {
  return useContext(SettingsContext);
}
