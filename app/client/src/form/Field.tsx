import { humanize, useLabel } from "../i18n";
import { readQualified, showQualified } from "../server/literals";
import type { FieldSpec } from "../server/types";
import { Combobox } from "./Combobox";
import { readInt, showText } from "./values";
import type { FormValue } from "./FormDraft";

type Props = {
  spec: FieldSpec;
  value: FormValue | undefined;
  columns: string[];
  legal: FormValue[] | null;
  onChange: (value: FormValue | undefined) => void;
};

export function Field({ spec, value, columns, legal, onChange }: Props) {
  const label = useLabel();
  if (spec.widget === "int") {
    return (
      <input
        type="number"
        data-testid={`input-${spec.at}`}
        value={showText(value)}
        onChange={(event) => onChange(readInt(event.target.value))}
      />
    );
  }
  if (spec.widget === "flag") {
    const settled = legal !== null && legal.length < 2;
    return (
      <input
        type="checkbox"
        data-testid={`input-${spec.at}`}
        checked={value === true}
        disabled={settled}
        onChange={(event) => onChange(event.target.checked)}
      />
    );
  }
  if (spec.widget === "pick") {
    const allowed = (spec.options ?? []).filter(
      (option) => legal === null || legal.includes(option),
    );
    return (
      <select
        data-testid={`input-${spec.at}`}
        value={showText(value)}
        onChange={(event) => onChange(event.target.value)}
      >
        {allowed.map((option) => (
          <option key={option} value={option}>
            {label(`enum.${option}`, humanize(option))}
          </option>
        ))}
      </select>
    );
  }
  if (spec.widget === "qualified") {
    return (
      <Combobox
        value={showQualified(qualifiedValue(value))}
        columns={columns}
        placeholder="u.id"
        onChange={(text) =>
          onChange(readQualified(text, spec.nullable === true))
        }
      />
    );
  }
  if (spec.widget === "column") {
    return (
      <Combobox
        value={showText(value)}
        columns={columns}
        placeholder="u.id"
        onChange={(text) => onChange(text)}
      />
    );
  }
  return (
    <input
      type="text"
      data-testid={`input-${spec.at}`}
      value={showText(value)}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function qualifiedValue(value: FormValue | undefined):
  | {
      source?: string;
      column: string;
    }
  | undefined {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    !("column" in value) ||
    typeof value.column !== "string"
  )
    return undefined;
  return {
    column: value.column,
    ...("source" in value && typeof value.source === "string"
      ? { source: value.source }
      : {}),
  };
}
