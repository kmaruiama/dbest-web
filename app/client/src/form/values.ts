import type { FieldSpec, FormValue, Node, Widget } from "../server/types";

export function showText(value: FormValue | undefined): string {
  return value === null || value === undefined || typeof value === "object"
    ? ""
    : String(value);
}

export function readInt(text: string): FormValue | undefined {
  return text.trim().length === 0 ? undefined : Number(text);
}

function blankFor(widget: Widget, spec: FieldSpec): FormValue {
  if (widget === "column") return spec.nullable ? null : "";
  if (widget === "int") return 0;
  if (widget === "flag") return false;
  if (widget === "pick") return spec.options[0] ?? "";
  return "";
}

export function blankRow(spec: FieldSpec): FormValue {
  if (spec.widget === "list") return blankFor(spec.item ?? "text", spec);
  const row: { [key: string]: FormValue } = {};
  for (const cell of spec.of) row[cell.at] = blankFor(cell.widget, cell);
  return row;
}

export function withoutPlaceholders(
  node: Node,
  fields: FieldSpec[],
  placeholder: string,
): Node {
  const blank = (value: FormValue): FormValue => {
    if (typeof value === "string")
      return value.split(".").every((part) => part === placeholder)
        ? ""
        : value;
    if (Array.isArray(value)) return value.map(blank);
    if (value === null || typeof value !== "object") return value;
    const fields: { [key: string]: FormValue } = {};
    for (const [key, field] of Object.entries(value))
      fields[key] = blank(field);
    return fields;
  };
  const draft: Node = { ...node };
  for (const field of fields) {
    const value = node[field.at];
    if (value !== undefined) draft[field.at] = blank(value);
  }
  return draft;
}

export function withField(
  node: Node,
  at: string,
  value: FormValue | undefined,
): Node {
  if (value !== undefined) return { ...node, [at]: value };
  const rest = { ...node };
  delete rest[at];
  return rest;
}

export function itemsAt(node: Node, at: string): FormValue[] {
  const items = node[at];
  return Array.isArray(items) ? items : [];
}

export function withItem(
  node: Node,
  at: string,
  index: number,
  value: FormValue,
): Node {
  return withField(
    node,
    at,
    itemsAt(node, at).map((item, position) =>
      position === index ? value : item,
    ),
  );
}

export function withoutItem(node: Node, at: string, index: number): Node {
  return withField(
    node,
    at,
    itemsAt(node, at).filter((_item, position) => position !== index),
  );
}

export function withAppended(node: Node, at: string, value: FormValue): Node {
  return withField(node, at, [...itemsAt(node, at), value]);
}

export function withCell(
  node: Node,
  at: string,
  index: number,
  cellAt: string,
  value: FormValue | undefined,
): Node {
  const item = itemsAt(node, at)[index];
  const row =
    item !== null && typeof item === "object" && !Array.isArray(item)
      ? item
      : {};
  const next = { ...row };
  if (value === undefined) delete next[cellAt];
  else next[cellAt] = value;
  return withItem(node, at, index, next);
}
