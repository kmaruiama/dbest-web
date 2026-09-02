import { CASES } from "./manifest";
import { expect, test } from "./harness";

test("every palette chip has a browser case", async ({ entry }) => {
  const covered = new Set(CASES.map((option) => option.chip));
  const missing = entry.operators
    .map((chip) => chip.key)
    .filter((key) => !covered.has(key));
  expect(
    missing,
    "these palette chips are never exercised in a browser",
  ).toEqual([]);
  const offered = new Set(entry.operators.map((chip) => chip.key));
  const unknown = [...covered].filter((key) => !offered.has(key));
  expect(unknown, "these cases name chips that are not in the catalog").toEqual(
    [],
  );
});
