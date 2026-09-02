import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  applyLang,
  DEFAULT_LANG,
  initialLang,
  label,
  translate,
  type Params,
} from "./core";
import type { Lang } from "./strings";

export { applyLang, humanize, initialLang, isLang, translate } from "./core";

export type { Params } from "./core";

export type { Lang };

type Context = {
  lang: Lang;
  setLang: (lang: Lang) => void;
};

const I18nContext = createContext<Context>({
  lang: DEFAULT_LANG,
  setLang: () => undefined,
});

type Props = {
  children: ReactNode;
};

export function I18nProvider({ children }: Props) {
  const [lang, setLang] = useState<Lang>(initialLang);
  const value = useMemo(
    () => ({
      lang,
      setLang: (next: Lang) => {
        applyLang(next);
        setLang(next);
      },
    }),
    [lang],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useLang(): Lang {
  return useContext(I18nContext).lang;
}

export function useSetLang(): (lang: Lang) => void {
  return useContext(I18nContext).setLang;
}

export function useTranslation(): (key: string, params?: Params) => string {
  const { lang } = useContext(I18nContext);
  return useCallback(
    (key: string, params?: Params) => translate(lang, key, params),
    [lang],
  );
}

export function useLabel(): (key: string, fallback: string) => string {
  const { lang } = useContext(I18nContext);
  return useCallback(
    (key: string, fallback: string) => label(lang, key, fallback),
    [lang],
  );
}
