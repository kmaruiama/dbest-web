import { expect, test } from "./harness";
import { TableModal } from "./pages/tables";

test("a hand-typed memory table goes on the canvas under its name and scans back its rows", async ({
  page,
  boot,
}) => {
  const { canvas, results } = await boot([]);
  const modal = new TableModal(page);
  await modal.open();
  await modal.named("nums");
  await modal.columns([
    { name: "n", type: "INT" },
    { name: "label", type: "STRING" },
  ]);
  await modal.rows([
    ["1", "one"],
    ["2", "two"],
  ]);
  await modal.save();
  const tableChip = page.getByTestId("table-1");
  await expect(tableChip).toBeVisible();
  await expect(tableChip).toHaveText("nums");
  await tableChip.click();
  await expect.poll(() => canvas.nodeIds()).toEqual([1]);
  await canvas.run(1);
  const read = await results.readAll();
  expect(read.schema).toEqual(["nums.n", "nums.label"]);
  expect(read.rows).toEqual([
    ["1", "one"],
    ["2", "two"],
  ]);
});

test("a table node's alias can be renamed after it is placed", async ({
  boot,
}) => {
  const { canvas, results } = await boot(["emp"]);
  const node = await canvas.addTable("emp", "e", { x: 160, y: 140 });
  await canvas.rename(node, "people");
  await canvas.run(node);
  const read = await results.readAll();
  expect(read.schema.every((column) => column.startsWith("people."))).toBe(
    true,
  );
});
