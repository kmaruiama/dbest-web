import { expect, test } from "./harness";

test("a session file's named nodes show their alias and qualify their rows with it", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("aliases");
  const alias = (id: number) => canvas.node(id).locator(".node-alias");
  await expect(alias(5)).toHaveText("as pessoas");
  await expect(alias(12)).toHaveText("as todos");
  await expect(alias(14)).toHaveText("as porDept");
  await expect(alias(21)).toHaveText("as linha");
  await expect(alias(9)).toHaveCount(0);
  await canvas.select(19);
  await expect(canvas.node(19).getByTestId("node-alias")).toHaveCount(0);

  await canvas.run(6);
  expect(await results.readAll()).toEqual({
    schema: ["pessoas.id", "pessoas.name", "pessoas.dept", "pessoas.tags"],
    rows: [
      ["1", "ana", "10", "x;y"],
      ["3", "cid", "10", "w"],
    ],
  });
  await results.dismiss();
  await canvas.run(15);
  expect(await results.readAll()).toEqual({
    schema: ["porDept.dept", "porDept.COUNT_id"],
    rows: [
      ["20", "1"],
      ["40", "1"],
    ],
  });
  await results.dismiss();
  await canvas.run(9);
  expect((await results.readAll()).schema).toEqual([
    "e3.id",
    "e3.name",
    "e3.dept",
    "e3.tags",
  ]);
});

test("naming an unnamed union renames its columns, and undo brings its inherited name back", async ({
  page,
  bootFile,
}) => {
  const { canvas, results } = await bootFile("aliases");
  await canvas.rename(9, "turma");
  await canvas.run(9);
  expect((await results.readAll()).schema).toEqual([
    "turma.id",
    "turma.name",
    "turma.dept",
    "turma.tags",
  ]);
  await results.dismiss();
  await page.getByRole("button", { name: "undo" }).click();
  await expect(canvas.node(9).locator(".node-alias")).toHaveCount(0);
  await canvas.run(9);
  expect((await results.readAll()).schema[0]).toBe("e3.id");
});

test("renaming a union renames the columns its readers take from it, below a join too, and one undo takes it all back", async ({
  page,
  bootFile,
}) => {
  const { canvas, results } = await bootFile("aliases");
  await canvas.rename(5, "gente");
  await expect.poll(() => canvas.problem(6)).toBeNull();
  await expect(canvas.node(6).locator(".node-caption")).toHaveText(
    "gente.dept = 10",
  );
  await canvas.run(6);
  expect(await results.readAll()).toEqual({
    schema: ["gente.id", "gente.name", "gente.dept", "gente.tags"],
    rows: [
      ["1", "ana", "10", "x;y"],
      ["3", "cid", "10", "w"],
    ],
  });
  await results.dismiss();

  await canvas.rename(26, "time");
  await expect(canvas.node(28).locator(".node-caption")).toHaveText(
    "dd.id = time.dept",
  );
  await canvas.run(29);
  expect((await results.readAll()).rows).toEqual([
    ["1", "ana", "10", "x;y"],
    ["3", "cid", "10", "w"],
  ]);
  await results.dismiss();

  await page.getByRole("button", { name: "undo" }).click();
  await expect(canvas.node(26).locator(".node-alias")).toHaveText("as grupo");
  await expect(canvas.node(28).locator(".node-caption")).toHaveText(
    "dd.id = grupo.dept",
  );
});

test("naming a blank union renames the name its readers inherited from its left input", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("aliases");
  await canvas.rename(9, "turma");
  await expect(canvas.node(30).locator(".node-caption")).toHaveText(
    "turma.dept = 10",
  );
  await canvas.run(30);
  expect(await results.readAll()).toEqual({
    schema: ["turma.id", "turma.name", "turma.dept", "turma.tags"],
    rows: [
      ["1", "ana", "10", "x;y"],
      ["3", "cid", "10", "w"],
    ],
  });
});

test("a rename to a name its tree already uses stays in the dialog with the reason", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("aliases");
  await canvas.select(5);
  await canvas.node(5).getByTestId("node-alias").click();
  const input = page.getByTestId("alias-input");
  await input.fill("e");
  await page.getByTestId("alias-confirm").click();
  await expect(page.getByTestId("alias-refused")).toContainText(
    "ja nomeia o node #1",
  );
  await expect(input).toBeVisible();
  await input.fill("gente");
  await page.getByTestId("alias-confirm").click();
  await expect(input).toBeHidden();
  await expect(canvas.node(5).locator(".node-alias")).toHaveText("as gente");
});

test("a second table of the same kind gets a name of its own, and joining two sources named alike is refused", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const first = await canvas.addTable("emp", "emp", { x: 120, y: 120 });
  const second = await canvas.addTable("emp", "emp", { x: 420, y: 120 });
  await expect(canvas.node(second).locator(".node-alias")).toHaveText(
    "as emp2",
  );
  await canvas.rename(second, "emp");
  const join = await canvas.dropChip("join", { x: 270, y: 340 });
  await canvas.link(first, join, "LEFT");
  await canvas.select(second);
  await canvas.node(second).getByTestId("node-link").click();
  await canvas.node(join).locator(".node-symbol").click();
  await expect(page.getByTestId("error-bar")).toContainText(
    "o alias 'emp' ja nomeia o node",
  );
  await expect(canvas.edge(second, join, "RIGHT")).toHaveCount(0);
});

test("a rename is saved: it survives a reload and closing and reopening the file", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("aliases");
  await canvas.rename(12, "tudo");
  await page.reload();
  await expect(canvas.node(12).locator(".node-alias")).toHaveText("as tudo");

  await page.locator(".tab.active .row-remove").click();
  const info = test.info();
  await page
    .getByRole("button", {
      name: `aliases-${info.testId}-${info.retry}`,
      exact: true,
    })
    .click();
  await expect(canvas.node(12).locator(".node-alias")).toHaveText("as tudo");
});

test("the alias dialog refuses a blank name and a name with a dot", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("aliases");
  await canvas.select(5);
  await canvas.node(5).getByTestId("node-alias").click();
  const input = page.getByTestId("alias-input");
  const confirm = page.getByTestId("alias-confirm");
  await expect(input).toHaveValue("pessoas");
  await input.fill("   ");
  await expect(confirm).toBeDisabled();
  await input.fill("a.b");
  await expect(confirm).toBeDisabled();
  await expect(page.getByTestId("alias-error")).toContainText("dot");
  await input.fill("minha turma");
  await expect(page.getByTestId("alias-error")).toHaveCount(0);
  await confirm.click();
  await expect(canvas.node(5).locator(".node-alias")).toHaveText(
    "as minha turma",
  );
});
