import type {
  ColumnReference,
  ComparisonCondition,
  Condition,
  GroupCondition,
  Literal,
  NullCheckCondition,
} from "../server/types";

export const CONDITION_SHAPES = [
  "comparison",
  "nullCheck",
  "notNull",
  "all",
  "any",
] as const;

export type ConditionShape = (typeof CONDITION_SHAPES)[number];

const emptyColumn = (): ColumnReference => ({ name: "" });

export const newComparison = (): ComparisonCondition => ({
  kind: "comparison",
  left: emptyColumn(),
  operator: "EQ",
  value: { str: "" },
});

export function replaceChild(
  group: GroupCondition,
  index: number,
  child: Condition,
): GroupCondition {
  return {
    ...group,
    children: group.children.map((old, at) => (at === index ? child : old)),
  };
}

export function removeChild(
  group: GroupCondition,
  index: number,
): GroupCondition {
  return {
    ...group,
    children: group.children.filter((_child, at) => at !== index),
  };
}

export function appendChild(group: GroupCondition): GroupCondition {
  return { ...group, children: [...group.children, newComparison()] };
}

export function changeShape(
  shape: ConditionShape,
  previous: Condition,
): Condition {
  const column =
    previous.kind === "comparison"
      ? previous.left
      : previous.kind === "nullCheck"
        ? previous.column
        : emptyColumn();

  if (shape === "comparison") return { ...newComparison(), left: column };
  if (shape === "nullCheck" || shape === "notNull") {
    return { kind: "nullCheck", column, negated: shape === "notNull" };
  }

  return {
    kind: "group",
    operator: shape === "all" ? "all" : "any",
    children: previous.kind === "group" ? previous.children : [previous],
  };
}

export function conditionShape(condition: Condition): ConditionShape {
  if (condition.kind === "comparison") return "comparison";
  if (condition.kind === "nullCheck")
    return condition.negated ? "notNull" : "nullCheck";
  return condition.operator;
}

export function withComparisonColumn(
  condition: ComparisonCondition,
  left: ColumnReference,
): ComparisonCondition {
  return { ...condition, left };
}

export function withComparisonOperator(
  condition: ComparisonCondition,
  operator: string,
): ComparisonCondition {
  return { ...condition, operator };
}

export function withComparisonValue(
  condition: ComparisonCondition,
  value: Literal,
): ComparisonCondition {
  return { ...condition, value };
}

export function withNullColumn(
  condition: NullCheckCondition,
  column: ColumnReference,
): NullCheckCondition {
  return { ...condition, column };
}
