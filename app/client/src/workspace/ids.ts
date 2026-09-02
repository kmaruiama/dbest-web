export function mint(
  taken: Iterable<number>,
  watermark: {
    current: number;
  },
): number {
  let highest = watermark.current;
  for (const id of taken) {
    if (id > highest) highest = id;
  }
  watermark.current = highest + 1;
  return watermark.current;
}
