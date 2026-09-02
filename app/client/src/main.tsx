import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { applyLang, I18nProvider, initialLang } from "./i18n";
import { App } from "./shell/App";
import { SettingsProvider } from "./shell/settings";
import { applyTheme, initialTheme } from "./shell/theme";
import { ErrorBoundary } from "./ui/ErrorBoundary";
import "./styles.css";

applyTheme(initialTheme());
applyLang(initialLang());

const root = document.getElementById("root");
if (root === null) throw new Error("#root is missing from index.html");
createRoot(root).render(
  <StrictMode>
    <I18nProvider>
      <ErrorBoundary>
        <SettingsProvider>
          <App />
        </SettingsProvider>
      </ErrorBoundary>
    </I18nProvider>
  </StrictMode>,
);
