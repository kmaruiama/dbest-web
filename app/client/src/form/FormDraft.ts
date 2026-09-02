import type { Condition } from "../server/types";

export type FormScalar = string | number | boolean | null;

export type FormValue =
  | FormScalar
  | Condition
  | FormValue[]
  | {
      [key: string]: FormValue;
    };

export type FormDraft = {
  [key: string]: FormValue;
};

export function field(draft: FormDraft, name: string): FormValue | undefined {
  return draft[name];
}

export function withField(
  draft: FormDraft,
  name: string,
  value: FormValue | undefined,
): FormDraft {
  if (value === undefined) {
    const { [name]: _removed, ...rest } = draft;
    return rest;
  }
  return { ...draft, [name]: value };
}

export function objectField(value: FormValue | undefined): {
  [key: string]: FormValue;
} {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value
    : {};
}

export function arrayField(value: FormValue | undefined): FormValue[] {
  return Array.isArray(value) ? value : [];
}
