import { test, expect } from "./harness";

test("copy and paste create one undoable canvas change", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot([]);
  const first = await canvas.dropChip("duplicateRemoval", { x: 120, y: 120 });
  const second = await canvas.dropChip("limit", { x: 360, y: 120 });
  await canvas.node(first).locator(".node-symbol").click({ button: "right" });
  await canvas
    .node(second)
    .locator(".node-symbol")
    .click({ button: "right", modifiers: ["Control"] });
  const pasted = page.waitForRequest((request) => {
    if (!request.url().endsWith("/commands") || request.method() !== "POST")
      return false;
    const body = request.postDataJSON() as {
      "@type"?: string;
      commands?: unknown[];
    };
    return body["@type"] === "batch" && body.commands?.length === 2;
  });
  await page.keyboard.press("Control+c");
  await page.keyboard.press("Control+v");
  await pasted;
  await expect
    .poll(() => canvas.nodeIds())
    .toEqual([first, second, second + 1, second + 2]);
  await expect(canvas.node(second + 1).locator(".node-box")).toHaveClass(
    /selected/,
  );
  await expect(canvas.node(second + 2).locator(".node-box")).toHaveClass(
    /selected/,
  );
  await expect(canvas.node(first).locator(".node-box")).not.toHaveClass(
    /selected/,
  );
  await expect(canvas.node(second).locator(".node-box")).not.toHaveClass(
    /selected/,
  );
  await page.getByRole("button", { name: "undo" }).click();
  await expect.poll(() => canvas.nodeIds()).toEqual([first, second]);
  await page.getByRole("button", { name: "redo" }).click();
  await expect
    .poll(() => canvas.nodeIds())
    .toEqual([first, second, second + 1, second + 2]);
});
test("deleting a selected node drops it, and undo brings it back", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot([]);
  const first = await canvas.dropChip("duplicateRemoval", { x: 120, y: 120 });
  const second = await canvas.dropChip("limit", { x: 360, y: 120 });
  await canvas.select(first);
  await canvas.node(first).getByTestId("node-delete").click();
  await expect.poll(() => canvas.nodeIds()).toEqual([second]);
  await page.getByRole("button", { name: "undo" }).click();
  await expect.poll(() => canvas.nodeIds()).toEqual([first, second]);
});
test("escape clears the selection", async ({ page, boot }) => {
  const { canvas } = await boot([]);
  const node = await canvas.dropChip("limit", { x: 160, y: 140 });
  await canvas.select(node);
  await expect(canvas.node(node).getByTestId("node-tools")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(canvas.node(node).getByTestId("node-tools")).toBeHidden();
});
test("moving a selection sends one batch", async ({ page, boot }) => {
  const { canvas } = await boot([]);
  const first = await canvas.dropChip("duplicateRemoval", { x: 120, y: 120 });
  const second = await canvas.dropChip("limit", { x: 360, y: 120 });
  await canvas.node(first).locator(".node-symbol").click({ button: "right" });
  await canvas
    .node(second)
    .locator(".node-symbol")
    .click({ button: "right", modifiers: ["Control"] });
  const moved = page.waitForRequest((request) => {
    if (!request.url().endsWith("/commands") || request.method() !== "POST")
      return false;
    const body = request.postDataJSON() as {
      "@type"?: string;
      commands?: {
        "@type"?: string;
      }[];
    };
    return (
      body["@type"] === "batch" &&
      body.commands?.length === 2 &&
      body.commands.every((command) => command["@type"] === "move")
    );
  });
  const box = await canvas.node(first).locator(".node-symbol").boundingBox();
  if (box === null) throw new Error("first selected node is not visible");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(
    box.x + box.width / 2 + 90,
    box.y + box.height / 2 + 70,
    { steps: 5 },
  );
  await page.mouse.up();
  await moved;
});
