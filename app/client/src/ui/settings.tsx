import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { readPreference, writePreference } from "./preferences";
import { applyTheme, initialTheme, type Theme } from "./theme";

const PALETTE_VISIBLE_KEY = "dbest.palette.visible";
const ENGINE_CLASS_KEY = "dbest.caption.class";
const EXPRESSION_KEY = "dbest.caption.expression";
const WIDTH_KEY = "dbest.palette.width";
const COLLAPSED_CATEGORIES_KEY = "dbest.palette.collapsed-categories";
const DEFAULT_WIDTH = 288;
const MIN_WIDTH = 180;
const MAX_WIDTH = 620;

export type Settings = {
  theme: Theme;
  paletteVisible: boolean;
  paletteWidth: number;
  collapsedCategories: Set<string>;
  showEngineClass: boolean;
  showExpression: boolean;
};

type Store = Settings & {
  update: (patch: Partial<Settings>) => void;
  dialogOpen: boolean;
  openDialog: () => void;
  closeDialog: () => void;
};

function storedFlag(key: string, fallback: boolean): boolean {
  return readPreference(key, fallback, (stored) => stored === "true");
}

function initialSettings(): Settings {
  localStorage.removeItem("dbest.axis");
  return {
    theme: initialTheme(),
    paletteWidth: initialWidth(),
    collapsedCategories: initialCollapsedCategories(),
    paletteVisible: storedFlag(PALETTE_VISIBLE_KEY, true),
    showEngineClass: storedFlag(ENGINE_CLASS_KEY, false),
    showExpression: storedFlag(EXPRESSION_KEY, true),
  };
}

function persist(patch: Partial<Settings>): void {
  if (patch.theme !== undefined) applyTheme(patch.theme);
  if (patch.paletteWidth !== undefined) {
    writePreference(WIDTH_KEY, String(patch.paletteWidth));
  }
  if (patch.collapsedCategories !== undefined) {
    writePreference(
      COLLAPSED_CATEGORIES_KEY,
      JSON.stringify([...patch.collapsedCategories]),
    );
  }
  if (patch.paletteVisible !== undefined) {
    writePreference(PALETTE_VISIBLE_KEY, String(patch.paletteVisible));
  }
  if (patch.showEngineClass !== undefined) {
    writePreference(ENGINE_CLASS_KEY, String(patch.showEngineClass));
  }
  if (patch.showExpression !== undefined) {
    writePreference(EXPRESSION_KEY, String(patch.showExpression));
  }
}

const SettingsContext = createContext<Store>({
  ...initialSettings(),
  update: () => undefined,
  dialogOpen: false,
  openDialog: () => undefined,
  closeDialog: () => undefined,
});

type Props = {
  children: ReactNode;
};

export function SettingsProvider({ children }: Props) {
  const [settings, setSettings] = useState<Settings>(initialSettings);
  const [dialogOpen, setDialogOpen] = useState(false);
  const value = useMemo<Store>(
    () => ({
      ...settings,
      update: (patch: Partial<Settings>) => {
        persist(patch);
        setSettings((current) => ({ ...current, ...patch }));
      },
      dialogOpen,
      openDialog: () => setDialogOpen(true),
      closeDialog: () => setDialogOpen(false),
    }),
    [settings, dialogOpen],
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

export function clampWidth(value: number): number {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, Math.round(value)));
}

function initialWidth(): number {
  return readPreference(WIDTH_KEY, DEFAULT_WIDTH, (text) => {
    const stored = Number(text);
    return stored > 0 ? clampWidth(stored) : DEFAULT_WIDTH;
  });
}

function initialCollapsedCategories(): Set<string> {
  return readPreference(
    COLLAPSED_CATEGORIES_KEY,
    new Set<string>(),
    collapsedCategories,
  );
}

function collapsedCategories(stored: string): Set<string> {
  try {
    const categories = JSON.parse(stored);
    return Array.isArray(categories)
      ? new Set(
          categories.filter(
            (category): category is string => typeof category === "string",
          ),
        )
      : new Set();
  } catch {
    return new Set();
  }
}
