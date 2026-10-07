import { expect, test } from "./harness";

test("a new operator's form opens with the fields it could not fill empty", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const table = await canvas.addTable("emp", "e", { x: 160, y: 120 });
  const filter = await canvas.dropChip("filter", { x: 160, y: 330 });
  await canvas.link(table, filter);
  await canvas.expectAutoForm();
  const condition = page.getByTestId("form").locator(".condition-head");
  await expect(condition.locator('[role="combobox"]')).toHaveValue("");
  await expect(condition.getByTestId("condition-right")).toHaveValue("");
});

test("picking a column closes the list, and clicking the field opens it again", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const table = await canvas.addTable("emp", "e", { x: 160, y: 120 });
  const projection = await canvas.dropChip("projection", { x: 160, y: 330 });
  await canvas.link(table, projection);
  await canvas.expectAutoForm();
  const field = page.getByTestId("form").locator('[role="combobox"]').first();
  const list = page.getByRole("listbox");
  await field.click();
  await expect(list).toBeVisible();
  await list.getByRole("option", { name: "e.name" }).click();
  await expect(list).toBeHidden();
  await expect(field).toHaveValue("e.name");
  await field.click();
  await expect(list).toBeVisible();
});

test("a union is edited and renamed from two separate menu entries; an intersection has no alias", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot([]);
  const union = await canvas.dropChip("hashUnion", { x: 160, y: 140 });
  const intersection = await canvas.dropChip("intersection", {
    x: 420,
    y: 140,
  });
  await canvas.openEdit(union);
  const form = page.getByTestId("form");
  await expect(form.getByTestId("field-kind")).toBeVisible();
  await expect(form.getByTestId("field-alias")).toHaveCount(0);
  await page.getByTestId("form-cancel").click();
  await canvas.rename(union, "equipe");
  await canvas.select(intersection);
  await expect(
    canvas.node(intersection).getByTestId("node-edit"),
  ).toBeVisible();
  await expect(canvas.node(intersection).getByTestId("node-alias")).toHaveCount(
    0,
  );
});
