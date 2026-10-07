import { expect, test } from "./harness";

test("two marked nodes from different trees are measured side by side", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp", "dep"]);
  const emp = await canvas.addTable("emp", "emp", { x: 120, y: 120 });
  const dep = await canvas.addTable("dep", "dep", { x: 420, y: 120 });
  const compare = page.getByTestId("compare");
  await canvas.mark(emp);
  await expect(canvas.node(emp).locator(".node-mark")).toHaveText("A");
  await expect(compare).toBeHidden();
  await canvas.mark(dep);
  await expect(canvas.node(dep).locator(".node-mark")).toHaveText("B");
  await compare.click();
  const comparator = page.getByTestId("comparator");
  const tuples = comparator.getByTestId("metric.tuples").locator("td");
  await expect(comparator.getByTestId("comparator-not-measured")).toBeVisible();
  await expect(tuples).toHaveCount(0);
  await comparator.getByTestId("comparator-measure").click();
  await expect(tuples.first()).toHaveText("4");
  await comparator.getByTestId("comparator-limit").fill("1");
  await comparator.getByTestId("comparator-measure").click();
  await expect(tuples).toHaveText(["1+", "1+"]);
  await comparator.getByTestId("comparator-all").click();
  await expect(tuples.first()).toHaveText("4");
});

test("a node in a marked node's tree cannot be marked", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const emp = await canvas.addTable("emp", "emp", { x: 120, y: 120 });
  const limit = await canvas.dropChip("limit", { x: 120, y: 320 });
  await canvas.link(emp, limit);
  await canvas.dismissAutoForm();
  await canvas.mark(emp);
  await canvas.mark(limit);
  await expect(page.getByTestId("error-bar")).toContainText("same tree");
  await expect(canvas.node(limit).locator(".node-mark")).toHaveCount(0);
});

test("linking two marked trees into one disables the comparison", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const emp = await canvas.addTable("emp", "emp", { x: 120, y: 120 });
  const limit = await canvas.dropChip("limit", { x: 120, y: 320 });
  await canvas.mark(emp);
  await canvas.mark(limit);
  const compare = page.getByTestId("compare");
  await expect(compare).toBeEnabled();
  await canvas.link(emp, limit);
  await canvas.dismissAutoForm();
  await expect(compare).toBeDisabled();
});

test("a hash and a nested-loop plan of one join cost the engine in different places", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  await canvas.mark(3);
  await canvas.mark(6);
  await page.getByTestId("compare").click();
  const comparator = page.getByTestId("comparator");
  const cells = (metric: string) =>
    comparator.getByTestId(`metric.${metric}`).locator("td");
  await comparator.getByTestId("comparator-measure").click();
  await expect(comparator.locator("thead th").nth(1)).toContainText("#3");
  await expect(comparator.locator("thead th").nth(2)).toContainText("#6");
  await expect(cells("tuples")).toHaveText(["3", "3"]);
  await expect(cells("comparisons")).toHaveText(["0", "12"]);
  await expect(cells("comparisons").first()).toHaveClass(/lower/);
  await expect(cells("memoryBytes").first()).toHaveText(/KB$/);
  await expect(cells("memoryBytes").nth(1)).toHaveText("0 KB");
  await expect(cells("memoryBytes").nth(1)).toHaveClass(/lower/);
  await expect(cells("tuples").first()).not.toHaveClass(/lower/);

  await comparator.getByTestId("comparator-limit").fill("1");
  await comparator.getByTestId("comparator-limit").press("Enter");
  await expect(cells("tuples")).toHaveText(["1+", "1+"]);
  await expect(cells("comparisons")).toHaveText(["0", "3"]);
  await comparator.getByTestId("comparator-all").click();
  await expect(cells("tuples")).toHaveText(["3", "3"]);

  await page.keyboard.press("Escape");
  await expect(comparator).toBeHidden();
});

test("the tuple count must be a positive whole number", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  await canvas.mark(3);
  await canvas.mark(8);
  await page.getByTestId("compare").click();
  const comparator = page.getByTestId("comparator");
  const limit = comparator.getByTestId("comparator-limit");
  const measure = comparator.getByTestId("comparator-measure");
  await expect(comparator.getByTestId("comparator-not-measured")).toBeVisible();
  for (const bad of ["0", "-2", "", "1.5"]) {
    await limit.fill(bad);
    await expect(measure, `"${bad}" was accepted`).toBeDisabled();
  }
  await limit.fill("2");
  await expect(measure).toBeEnabled();
  await measure.click();
  await expect(
    comparator.getByTestId("metric.tuples").locator("td"),
  ).toHaveText(["2+", "2+"]);
});

test("a third mark is refused, and unmarking A makes B the new A", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  const badge = (id: number) => canvas.node(id).locator(".node-mark");
  await canvas.mark(3);
  await expect(page.getByText("mark a node from another tree")).toBeVisible();
  await canvas.mark(6);
  await canvas.mark(8);
  await expect(page.getByTestId("error-bar")).toContainText(
    "two nodes are already marked",
  );
  await expect(badge(8)).toHaveCount(0);
  await canvas.mark(3);
  await expect(badge(3)).toHaveCount(0);
  await expect(badge(6)).toHaveText("A");
  await canvas.mark(8);
  await expect(badge(8)).toHaveText("B");
});

test("clearing the marks or deleting a marked node takes the comparison away, and undo brings it back", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  const compare = page.getByTestId("compare");
  await canvas.mark(3);
  await canvas.mark(6);
  await expect(compare).toBeVisible();
  await page.getByTestId("clear-marks").click();
  await expect(compare).toBeHidden();
  await expect(page.locator(".node-mark")).toHaveCount(0);

  await canvas.mark(3);
  await canvas.mark(6);
  await canvas.select(6);
  await canvas.node(6).getByTestId("node-delete").click();
  await expect(canvas.node(6)).toHaveCount(0);
  await expect(compare).toBeHidden();
  await expect(page.getByText("mark a node from another tree")).toBeVisible();
  await page.getByRole("button", { name: "undo" }).click();
  await expect(canvas.node(6).locator(".node-mark")).toHaveText("B");
  await expect(compare).toBeEnabled();
});

test("an unfinished plan and a correlated node say why they cannot be measured", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  const comparator = page.getByTestId("comparator");
  await canvas.mark(9);
  await canvas.mark(3);
  await page.getByTestId("compare").click();
  await comparator.getByTestId("comparator-measure").click();
  await expect(comparator.locator(".detail")).toContainText(
    "esta sem sua entrada",
  );
  await page.keyboard.press("Escape");
  await page.getByTestId("clear-marks").click();

  await canvas.mark(12);
  await canvas.mark(3);
  await page.getByTestId("compare").click();
  await comparator.getByTestId("comparator-measure").click();
  await expect(comparator.locator(".detail")).toContainText(
    "vindas do join #13",
  );
  await page.keyboard.press("Escape");
  await page.getByTestId("clear-marks").click();

  await canvas.mark(13);
  await canvas.mark(3);
  await page.getByTestId("compare").click();
  await comparator.getByTestId("comparator-measure").click();
  await expect(
    comparator.getByTestId("metric.tuples").locator("td"),
  ).toHaveText(["2", "3"]);
});

test("two plans fed by one table node are one tree", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("comparator");
  await canvas.mark(15);
  await canvas.mark(16);
  await expect(page.getByTestId("error-bar")).toContainText("same tree");
  await expect(canvas.node(16).locator(".node-mark")).toHaveCount(0);
});
