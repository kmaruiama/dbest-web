import type { FormValue } from "../form/FormDraft";
import type { Arity, Catalog, Chip, FieldSpec, PlanNode, Port } from "./types";

let installed: Catalog | null = null;

export function installCatalog(loaded: Catalog): void {
  installed = loaded;
}

export function catalog(): Catalog {
  if (installed === null) throw new Error("catalog read before boot finished");
  return installed;
}

export function categories(): string[] {
  return catalog().categories;
}

export function chipsIn(category: string): Chip[] {
  return catalog().operators.filter((chip) => chip.category === category);
}

export function chipByKey(key: string): Chip | undefined {
  return catalog().operators.find((chip) => chip.key === key);
}

function arityOf(type: string): Arity {
  if (type === "table") return "source";
  return catalog().types[type]?.arity ?? "unary";
}

export function inputPorts(type: string): Port[] {
  const arity = arityOf(type);
  if (arity === "source") return [];
  if (arity === "binary") return ["LEFT", "RIGHT"];
  return ["ONLY"];
}

export function isEditable(type: string): boolean {
  return catalog().types[type]?.editable ?? false;
}

export function fieldsOf(type: string): FieldSpec[] {
  return catalog().types[type]?.fields ?? [];
}

export function chipOf(node: PlanNode): Chip | undefined {
  const type = node.kind;
  const chips = catalog().operators.filter((chip) => chip.type === type);
  if (chips.length === 0) return undefined;
  const variants = catalog().types[type]?.variants;
  if (variants === undefined || variants.length === 0) return chips[0];
  return chips.find((chip) =>
    variants.every(
      (field) => chip.template.fields[field] === node.fields[field],
    ),
  );
}

export function symbolOf(node: PlanNode): {
  symbol: string;
  known: boolean;
} {
  if (node.kind === "table") return { symbol: "▤", known: true };
  const chip = chipOf(node);
  if (chip === undefined) return { symbol: "?", known: false };
  return { symbol: chip.symbol, known: true };
}

export function variantOptions(node: PlanNode, at: string): FormValue[] | null {
  const type = node.kind;
  const variants = catalog().types[type]?.variants;
  if (variants === undefined || !variants.includes(at)) return null;
  const others = variants.filter((field) => field !== at);
  const legal: FormValue[] = [];
  for (const chip of catalog().operators) {
    if (chip.type !== type) continue;
    if (
      !others.every(
        (field) => chip.template.fields[field] === node.fields[field],
      )
    )
      continue;
    const value = chip.template.fields[at];
    if (value === undefined) continue;
    if (!legal.includes(value)) legal.push(value);
  }
  return legal;
}
