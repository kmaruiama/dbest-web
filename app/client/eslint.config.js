import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";

const FEATURES = ["canvas", "form", "results", "tables"];
const SHARED = ["server", "ui", "i18n"];

function importsOnly(files, forbidden, message) {
  return {
    files,
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: forbidden.map((folder) => `../${folder}/*`),
              message,
            },
          ],
        },
      ],
    },
  };
}

export default tseslint.config(
  { ignores: ["dist", "node_modules", "test-results", "playwright-report"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx,mts,cts,mjs}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.node },
    },
    plugins: {
      "react-hooks": reactHooks,
    },
    rules: {
      ...reactHooks.configs["recommended-latest"].rules,
      "react-hooks/set-state-in-effect": "error",
      "react-hooks/refs": "error",
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
    },
  },
  {
    files: ["tests/**", "scripts/**", "*.config.{ts,js}"],
    rules: {
      "react-hooks/rules-of-hooks": "off",
      "no-empty": ["error", { allowEmptyCatch: true }],
      "no-control-regex": "off",
    },
  },
  importsOnly(
    SHARED.map((folder) => `src/${folder}/**`),
    [...FEATURES, "workspace", "app"],
    "shared code (server, ui, i18n) imports only shared code",
  ),
  ...FEATURES.map((feature) =>
    importsOnly(
      [`src/${feature}/**`],
      [...FEATURES.filter((other) => other !== feature), "workspace", "app"],
      "a feature imports only shared code (server, ui, i18n), never another feature or the screens above it",
    ),
  ),
  importsOnly(
    ["src/workspace/**"],
    ["app"],
    "the workspace imports features and shared code, never the app above it",
  ),
);
