import type { Columns, Port, Widget } from "../server/types";

export function offeredColumns(
  columns: Columns,
  widget: Widget,
  hasInputs: boolean,
  port?: Port,
): string[] {
  const inputs =
    port === undefined
      ? Object.values(columns.inputs).flat()
      : (columns.inputs[port] ?? []);
  const outer = widget === "condition" || !hasInputs ? columns.outer : [];
  return [...new Set([...inputs, ...outer])];
}
