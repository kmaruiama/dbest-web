import type { APIRequestContext } from "@playwright/test";
import { BACKEND_URL } from "../env";

export type Widget =
  | "text"
  | "int"
  | "flag"
  | "column"
  | "qualified"
  | "pick"
  | "condition"
  | "list"
  | "rows";

export type FieldSpec = {
  at: string;
  widget: Widget;
  options?: string[];
  item?: Widget;
  of?: FieldSpec[];
  nullable?: boolean;
};

export type Chip = {
  key: string;
  type: string;
  symbol: string;
  category: string;
};

export type Catalog = {
  categories: string[];
  types: Record<
    string,
    {
      arity: string;
      editable: boolean;
      fields?: FieldSpec[];
    }
  >;
  operators: Chip[];
};

let cached: Catalog | null = null;

export async function catalog(request: APIRequestContext): Promise<Catalog> {
  if (cached !== null) return cached;
  const response = await request.get(`${BACKEND_URL}/operators`);
  if (!response.ok()) throw new Error(`GET /operators -> ${response.status()}`);
  cached = (await response.json()) as Catalog;
  return cached;
}

export function fieldsOf(entry: Catalog, kind: string): FieldSpec[] {
  return entry.types[kind]?.fields ?? [];
}

export function specFor(
  entry: Catalog,
  kind: string,
  at: string,
): FieldSpec | undefined {
  return fieldsOf(entry, kind).find((field) => field.at === at);
}

export function chipFor(entry: Catalog, key: string): Chip {
  const chip = entry.operators.find((option) => option.key === key);
  if (chip === undefined) throw new Error(`no palette chip named "${key}"`);
  return chip;
}
