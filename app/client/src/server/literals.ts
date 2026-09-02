import type { ColumnReference, Literal } from "./types";

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

export function showQualified(
  value:
    | {
        source?: string;
        column: string;
      }
    | null
    | undefined,
): string {
  if (value === null || value === undefined) return "";
  const source = value.source ?? "";
  const name = value.column;
  if (source.length === 0 && name.length === 0) return "";
  return `${source}.${name}`;
}

export function readQualified(
  text: string,
  nullable: boolean,
): {
  source: string;
  column: string;
} | null {
  const trimmed = text.trim();
  if (trimmed.length === 0) return nullable ? null : { source: "", column: "" };
  const dot = trimmed.indexOf(".");
  if (dot === -1) return { source: "", column: trimmed };
  return { source: trimmed.slice(0, dot), column: trimmed.slice(dot + 1) };
}

export function showColumnRef(value: ColumnReference): string {
  return value.source === undefined
    ? value.name
    : `${value.source}.${value.name}`;
}

export function readColumnRef(text: string): ColumnReference {
  const trimmed = text.trim();
  const dot = trimmed.indexOf(".");
  if (dot === -1) return { name: trimmed };
  return { source: trimmed.slice(0, dot), name: trimmed.slice(dot + 1) };
}

export const COLUMN_TYPES = [
  "INT",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "STRING",
  "BOOLEAN",
] as const;

export type ColumnType = (typeof COLUMN_TYPES)[number];

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
