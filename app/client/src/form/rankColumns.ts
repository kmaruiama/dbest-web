export function rankColumns(
  columns: readonly string[],
  query: string,
): string[] {
  const needle = query.trim().toLowerCase();
  if (needle === "") return [...columns];
  return columns
    .map((column, index) => ({
      column,
      index,
      proximity: column.toLowerCase().indexOf(needle),
    }))
    .sort((left, right) => {
      const leftRank = left.proximity === -1 ? Infinity : left.proximity;
      const rightRank = right.proximity === -1 ? Infinity : right.proximity;
      return leftRank - rightRank || left.index - right.index;
    })
    .map(({ column }) => column);
}
