import {
  arrayField,
  field,
  objectField,
  type FormValue,
  withField as updateField,
} from "./FormDraft";
import type { FieldSpec, PlanNode, Widget } from "../server/types";

export function showText(value: FormValue | undefined): string {
  return value === null || value === undefined || typeof value === "object"
    ? ""
    : String(value);
}

export function readInt(text: string): FormValue | undefined {
  return text.trim().length === 0 ? undefined : Number(text);
}

function blankFor(widget: Widget, spec: FieldSpec): FormValue {
  if (widget === "qualified")
    return spec.nullable === true ? null : { source: "", column: "" };
  if (widget === "int") return 0;
  if (widget === "flag") return false;
  if (widget === "pick") return spec.options?.[0] ?? "";
  return "";
}

export function blankRow(spec: FieldSpec): FormValue {
  if (spec.widget === "list") return blankFor(spec.item ?? "text", spec);
  const row: {
    [key: string]: FormValue;
  } = {};
  for (const cell of spec.of ?? []) row[cell.at] = blankFor(cell.widget, cell);
  return row;
}

export function withField(
  node: PlanNode,
  at: string,
  value: FormValue | undefined,
): PlanNode {
  return { ...node, fields: updateField(node.fields, at, value) };
}

export function itemsAt(node: PlanNode, at: string): FormValue[] {
  return arrayField(field(node.fields, at));
}

export function withItem(
  node: PlanNode,
  at: string,
  index: number,
  value: FormValue,
): PlanNode {
  return withField(
    node,
    at,
    itemsAt(node, at).map((item, position) =>
      position === index ? value : item,
    ),
  );
}

export function withoutItem(
  node: PlanNode,
  at: string,
  index: number,
): PlanNode {
  return withField(
    node,
    at,
    itemsAt(node, at).filter((_item, position) => position !== index),
  );
}

export function withAppended(
  node: PlanNode,
  at: string,
  value: FormValue,
): PlanNode {
  return withField(node, at, [...itemsAt(node, at), value]);
}

export function withCell(
  node: PlanNode,
  at: string,
  index: number,
  cellAt: string,
  value: FormValue | undefined,
): PlanNode {
  const row = objectField(itemsAt(node, at)[index]);
  return withItem(node, at, index, updateField(row, cellAt, value));
}
