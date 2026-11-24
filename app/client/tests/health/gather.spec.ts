import type { Page } from "@playwright/test";
import { expect, test } from "./harness";
import type { Canvas } from "./pages/canvas";
import type { Form } from "./pages/form";

async function parallelTable(
  canvas: Canvas,
  form: Form,
  at: { x: number; y: number },
): Promise<number> {
  const table = await canvas.addTable("empTree", "e", at);
  await canvas.openEdit(table);
  await form.fill("table", [{ at: "parallel", flag: true }]);
  await form.save();
  return table;
}

async function gatherOver(
  canvas: Canvas,
  form: Form,
  input: number,
  threads: number,
  at: { x: number; y: number },
): Promise<number> {
  const gather = await canvas.dropChip("gather", at);
  await canvas.link(input, gather);
  await canvas.expectAutoForm();
  await form.fill("gather", [{ at: "threads", int: threads }]);
  await form.save();
  return gather;
}

const undo = (page: Page) => page.getByRole("button", { name: /undo/ });

test("a parallel table says so on the canvas, survives a reload and undoes", async ({
  page,
  boot,
  consoleErrors,
}) => {
  const { canvas, form } = await boot(["empTree"]);
  const table = await parallelTable(canvas, form, { x: 160, y: 120 });
  const caption = canvas.node(table).locator(".node-caption");
  await expect(caption).toHaveText("emptree ∥");
  await page.reload();
  await expect(caption).toHaveText("emptree ∥");
  await undo(page).click();
  await expect(caption).toHaveText("emptree");
  expect(consoleErrors).toEqual([]);
});

test("a gather pages through the whole parallel table in key order and exports it", async ({
  page,
  boot,
  consoleErrors,
}) => {
  const { canvas, form, results } = await boot(["empTree"]);
  const table = await parallelTable(canvas, form, { x: 160, y: 120 });
  const gather = await gatherOver(canvas, form, table, 8, { x: 160, y: 340 });
  await expect(canvas.node(gather).locator(".node-caption")).toHaveText(
    "8 threads",
  );
  await canvas.run(gather);
  const firstId = page
    .getByTestId("results-table")
    .locator("tbody tr")
    .first()
    .locator("td")
    .first();
  await expect(firstId).toHaveText("1");
  await page.getByTestId("results-next").click();
  await expect(firstId).toHaveText("51");
  await page.getByTestId("results-all").click();
  await expect.poll(() => results.total()).toMatch(/2[.,]?000/);
  const csv = page.getByTestId("results").locator("a", { hasText: "CSV" });
  const exported = await page.request.get(
    (await csv.getAttribute("href")) as string,
  );
  expect(exported.ok()).toBe(true);
  const lines = (await exported.text()).trim().split("\n");
  expect(lines.length).toBe(2001);
  await results.dismiss();
  await canvas.run(gather);
  await results.dismiss();
  expect(consoleErrors).toEqual([]);
});

test("the gather form refuses thread counts outside 1 to 64", async ({
  page,
  boot,
}) => {
  const { canvas, form } = await boot(["empTree"]);
  const table = await parallelTable(canvas, form, { x: 160, y: 120 });
  const gather = await canvas.dropChip("gather", { x: 160, y: 340 });
  await canvas.link(table, gather);
  await canvas.expectAutoForm();
  for (const threads of [0, -3, 65]) {
    await form.fill("gather", [{ at: "threads", int: threads }]);
    await page.getByTestId("form-save").click();
    await expect(page.getByTestId("form-error")).toContainText("1 a 64");
  }
  await form.fill("gather", [{ at: "threads", int: 64 }]);
  await form.save();
  await expect(canvas.node(gather).locator(".node-caption")).toHaveText(
    "64 threads",
  );
});

test("gather plans that cannot run say why", async ({ page, boot }) => {
  const { canvas, form, results } = await boot(["empTree", "emp"]);
  const serial = await canvas.addTable("empTree", "s", { x: 160, y: 120 });
  const plain = await gatherOver(canvas, form, serial, 2, { x: 160, y: 340 });
  await canvas.run(plain);
  expect(await results.failure()).toContain("uma tabela paralela");
  await results.dismiss();

  const table = await parallelTable(canvas, form, { x: 520, y: 120 });
  const inner = await gatherOver(canvas, form, table, 2, { x: 520, y: 340 });
  const outer = await gatherOver(canvas, form, inner, 2, { x: 520, y: 560 });
  await canvas.run(outer);
  expect(await results.failure()).toContain("outro Gather");
  await results.dismiss();

  const memory = await canvas.addTable("emp", "m", { x: 880, y: 120 });
  await canvas.openEdit(memory);
  await form.fill("table", [{ at: "parallel", flag: true }]);
  await form.save();
  await canvas.run(memory);
  expect(await results.failure()).toContain("B-tree");
  await results.dismiss();
  await expect(page.getByTestId("results")).toBeHidden();
});

test("the comparator measures a gather against its serial twin", async ({
  page,
  boot,
}) => {
  const { canvas, form } = await boot(["empTree"]);
  const table = await parallelTable(canvas, form, { x: 160, y: 120 });
  const gather = await gatherOver(canvas, form, table, 4, { x: 160, y: 340 });
  const serial = await canvas.addTable("empTree", "s", { x: 520, y: 120 });
  await canvas.mark(gather);
  await canvas.mark(serial);
  await page.getByTestId("compare").click();
  const comparator = page.getByTestId("comparator");
  await comparator.getByTestId("comparator-all").click();
  const cells = (metric: string) =>
    comparator.getByTestId(`metric.${metric}`).locator("td");
  await expect(cells("tuples")).toHaveText([/^2[.,]?000$/, /^2[.,]?000$/]);
  await expect(cells("recordsRead")).toHaveText([/^2[.,]?000$/, /^2[.,]?000$/]);
});
