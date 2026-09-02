import { CASES } from "./manifest";
import { runCase } from "./runner";
import { expect, test } from "./harness";

for (const entry of CASES) {
  const title = `${entry.chip}: ${entry.name}`;
  test(title, async ({ page, boot, entry: catalog, consoleErrors }) => {
    await boot(entry.tables);
    const outcome = await runCase(page, catalog, entry);
    if (entry.broken !== undefined) {
      expect(
        outcome.ok,
        `marked broken but now passes -- drop the note:\n  ${entry.broken}`,
      ).toBe(false);
      return;
    }
    expect(outcome.ok ? "" : outcome.why).toBe("");
    expect(
      consoleErrors,
      "the browser logged errors during this scenario",
    ).toEqual([]);
  });
}
