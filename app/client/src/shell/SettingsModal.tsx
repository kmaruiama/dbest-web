import { useEffect, useState, type ReactNode } from "react";
import { isLang, useLang, useSetLang, useTranslation } from "../i18n";
import { LANGS } from "../i18n/strings";
import { useSettings } from "./settings";
import { isTheme, THEMES } from "./theme";

const LANG_NAME: Record<string, string> = {
  "en-US": "English",
  "pt-BR": "Português",
};

export function useSettingsDialog(): {
  gear: ReactNode;
  dialog: ReactNode;
} {
  const translate = useTranslation();
  const [open, setOpen] = useState(false);
  return {
    gear: (
      <button
        type="button"
        className="gear"
        title={translate("settings")}
        aria-label={translate("settings")}
        onClick={() => setOpen(true)}
      >
        ⚙
      </button>
    ),
    dialog: open ? <SettingsModal onClose={() => setOpen(false)} /> : null,
  };
}

type Props = {
  onClose: () => void;
};

function SettingsModal({ onClose }: Props) {
  const translate = useTranslation();
  const lang = useLang();
  const setLang = useSetLang();
  const settings = useSettings();
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className="dialog form"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2>{translate("settings")}</h2>

        <div className="field">
          <label>{translate("theme")}</label>
          <select
            className="chrome-select"
            value={settings.theme}
            onChange={(event) => {
              if (isTheme(event.target.value))
                settings.update({ theme: event.target.value });
            }}
          >
            {THEMES.map((key) => (
              <option key={key} value={key}>
                {translate(`theme.${key}`)}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label>{translate("language")}</label>
          <select
            className="chrome-select"
            value={lang}
            onChange={(event) => {
              if (isLang(event.target.value)) setLang(event.target.value);
            }}
          >
            {LANGS.map((key) => (
              <option key={key} value={key}>
                {LANG_NAME[key] ?? key}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label>{translate("captions")}</label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.showEngineClass}
              onChange={(event) =>
                settings.update({
                  showEngineClass: event.target.checked,
                })
              }
            />
            {translate("showEngineClass")}
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.showExpression}
              onChange={(event) =>
                settings.update({
                  showExpression: event.target.checked,
                })
              }
            />
            {translate("showExpression")}
          </label>
        </div>

        <label className="check">
          <input
            type="checkbox"
            checked={settings.paletteVisible}
            onChange={(event) =>
              settings.update({
                paletteVisible: event.target.checked,
              })
            }
          />
          {translate("showPaletteSetting")}
        </label>

        <div className="dialog-actions">
          <button type="button" className="btn-ghost" onClick={onClose}>
            {translate("close")}
          </button>
        </div>
      </div>
    </div>
  );
}
