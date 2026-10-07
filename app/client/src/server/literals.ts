import type { ColumnType, Literal, SchemaColumn } from "./types";

type LiteralTag = "int" | "long" | "float" | "double" | "bool" | "str";

const READ: Record<LiteralTag, (text: string) => unknown> = {
  int: (text) => Number.parseInt(text, 10),
  long: (text) => Number.parseInt(text, 10),
  float: (text) => Number.parseFloat(text),
  double: (text) => Number.parseFloat(text),
  bool: (text) => /^true$/i.test(text),
  str: (text) => text,
};

const INTEGER = /^-?\d+$/;
const DECIMAL = /^-?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i;
const BOOLEAN = /^(?:true|false)$/i;

function tagOf(text: string): LiteralTag {
  const trimmed = text.trim();
  if (INTEGER.test(trimmed)) return "int";
  if (DECIMAL.test(trimmed)) return "double";
  if (BOOLEAN.test(trimmed)) return "bool";
  return "str";
}

function literalOf(tag: LiteralTag, text: string): Literal {
  const value = READ[tag](text.trim());
  if (tag === "bool") return { bool: value === true };
  if (tag === "str") return { str: String(value) };
  return { [tag]: Number(value) } as Literal;
}

export function readLiteral(text: string): Literal {
  return literalOf(tagOf(text), text);
}

export function showLiteral(value: Literal): string {
  const only = Object.values(value)[0];
  return String(only);
}

export function showRawValue(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}

export function qualifiedName(column: SchemaColumn): string {
  return `${column.source}.${column.name}`;
}

export const COLUMN_TYPES: ColumnType[] = [
  "INT",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "STRING",
  "BOOLEAN",
];

const COLUMNS: Record<
  ColumnType,
  {
    tag: LiteralTag;
    accepts: LiteralTag[];
  }
> = {
  INT: { tag: "int", accepts: ["int"] },
  LONG: { tag: "long", accepts: ["int"] },
  FLOAT: { tag: "float", accepts: ["int", "double"] },
  DOUBLE: { tag: "double", accepts: ["int", "double"] },
  BOOLEAN: { tag: "bool", accepts: ["bool"] },
  STRING: { tag: "str", accepts: ["int", "double", "bool", "str"] },
};

const COLUMN_FOR: Record<LiteralTag, ColumnType> = {
  int: "INT",
  long: "LONG",
  float: "FLOAT",
  double: "DOUBLE",
  bool: "BOOLEAN",
  str: "STRING",
};

export function columnLiteral(type: ColumnType, text: string): Literal | null {
  return text.trim().length === 0 ? null : literalOf(COLUMNS[type].tag, text);
}

export function acceptsLiteral(type: ColumnType, text: string): boolean {
  return (
    text.trim().length === 0 || COLUMNS[type].accepts.includes(tagOf(text))
  );
}

export function columnTypeOf(sample: string): ColumnType {
  return COLUMN_FOR[tagOf(sample)];
}
