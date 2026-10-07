import { readLiteral, showLiteral } from "../server/literals";
import type { Comparison, Condition, Literal } from "../server/types";

export const CONDITION_SHAPES: Condition["@type"][] = [
  "cmp",
  "isNull",
  "isNotNull",
  "and",
  "or",
];

export function newComparison(): Comparison {
  return { "@type": "cmp", left: "", op: "EQ", right: { str: "" } };
}

export function changeShape(
  shape: Condition["@type"],
  previous: Condition,
): Condition {
  const column =
    previous["@type"] === "cmp"
      ? previous.left
      : previous["@type"] === "isNull" || previous["@type"] === "isNotNull"
        ? previous.column
        : "";
  if (shape === "cmp") return { ...newComparison(), left: column };
  if (shape === "isNull" || shape === "isNotNull")
    return { "@type": shape, column };
  const grouped =
    previous["@type"] === "and" || previous["@type"] === "or"
      ? previous.conditions
      : [previous];
  return { "@type": shape, conditions: grouped };
}

export function isCondition(value: unknown): value is Condition {
  if (typeof value !== "object" || value === null || !("@type" in value))
    return false;
  return CONDITION_SHAPES.includes(value["@type"] as Condition["@type"]);
}

export type Operand = "value" | "column";

export const OPERANDS: Operand[] = ["value", "column"];

export function operandOf(right: Literal): Operand {
  return "ref" in right ? "column" : "value";
}

export function withOperand(right: Literal, operand: Operand): Literal {
  if (operandOf(right) === operand) return right;
  const text = showLiteral(right).trim();
  return operand === "column" ? { ref: text } : readLiteral(text);
}
