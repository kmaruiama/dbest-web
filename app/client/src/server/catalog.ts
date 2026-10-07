import type {
  Arity,
  Catalog,
  Chip,
  FieldSpec,
  FormValue,
  Node,
  Port,
} from "./types";

export function categories(catalog: Catalog): string[] {
  return catalog.categories;
}

export function chipsIn(catalog: Catalog, category: string): Chip[] {
  return catalog.operators.filter((chip) => chip.category === category);
}

export function chipByKey(catalog: Catalog, key: string): Chip | undefined {
  return catalog.operators.find((chip) => chip.key === key);
}

export function inputPorts(catalog: Catalog, type: string): Port[] {
  const arity = arityOf(catalog, type);
  if (arity === "source") return [];
  if (arity === "binary") return ["LEFT", "RIGHT"];
  return ["ONLY"];
}

function arityOf(catalog: Catalog, type: string): Arity {
  if (type === "table") return "source";
  return catalog.types[type]?.arity ?? "unary";
}

export function isEditable(catalog: Catalog, type: string): boolean {
  return catalog.types[type]?.editable ?? false;
}

export function fieldsOf(catalog: Catalog, type: string): FieldSpec[] {
  return catalog.types[type]?.fields ?? [];
}

export function chipOf(catalog: Catalog, node: Node): Chip | undefined {
  const type = node["@type"];
  const variants = catalog.types[type]?.variants ?? [];
  return catalog.operators.find(
    (chip) =>
      chip.type === type &&
      variants.every((field) => chip.template[field] === node[field]),
  );
}

export function symbolOf(
  catalog: Catalog,
  node: Node,
): { symbol: string; known: boolean } {
  if (node["@type"] === "table") return { symbol: "▤", known: true };
  const chip = chipOf(catalog, node);
  if (chip === undefined) return { symbol: "?", known: false };
  return { symbol: chip.symbol, known: true };
}

export function variantOptions(
  catalog: Catalog,
  node: Node,
  at: string,
): FormValue[] | null {
  const type = node["@type"];
  const variants = catalog.types[type]?.variants ?? [];
  if (!variants.includes(at)) return null;
  const others = variants.filter((field) => field !== at);
  const legal: FormValue[] = [];
  for (const chip of catalog.operators) {
    if (chip.type !== type) continue;
    if (!others.every((field) => chip.template[field] === node[field]))
      continue;
    const value = chip.template[at];
    if (value !== undefined && !legal.includes(value)) legal.push(value);
  }
  return legal;
}
