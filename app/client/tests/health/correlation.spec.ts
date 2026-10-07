import { expect, test } from "./harness";

const ANSWERS: { root: number; rows: string[][] }[] = [
  {
    root: 4,
    rows: [
      ["10", "sp"],
      ["20", "rj"],
    ],
  },
  { root: 8, rows: [["30", "bh"]] },
  {
    root: 14,
    rows: [
      ["2", "bob", "20", "z"],
      ["3", "cid", "10", "w"],
    ],
  },
  {
    root: 17,
    rows: [
      ["1", "ana", "10", "x;y", "10"],
      ["2", "bob", "20", "z", "20"],
      ["3", "cid", "10", "w", "10"],
      ["4", "dan", "40", "q", "40"],
    ],
  },
  {
    root: 21,
    rows: [
      ["1", "ana", "10", "x;y", "10", "sp"],
      ["2", "bob", "20", "z", "20", "rj"],
      ["3", "cid", "10", "w", "10", "sp"],
      ["4", "dan", "40", "q", "", ""],
    ],
  },
  {
    root: 27,
    rows: [
      ["10", "sp"],
      ["20", "rj"],
    ],
  },
];

test("each correlated plan in a session file answers as its SQL would", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("correlation");
  for (const answer of ANSWERS) {
    await canvas.run(answer.root);
    expect((await results.readAll()).rows, `root #${answer.root}`).toEqual(
      answer.rows,
    );
    await results.dismiss();
  }
  await canvas.run(17);
  expect((await results.readAll()).schema).toContain("atual.func_dept");
});

test("a correlated filter run alone names the join to run instead", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("correlation");
  await canvas.run(3);
  expect(await results.failure()).toContain("vindas do join #4");
  await results.dismiss();
  await canvas.run(25);
  expect(await results.failure()).toContain("vindas do join #26");
});

test("exporting a correlated filter alone is refused, and its join exports its rows", async ({
  page,
  bootFile,
}) => {
  await bootFile("correlation");
  const sid = (
    (await (await page.request.get("/sessions")).json()) as { sid: string }[]
  )[0].sid;
  const alone = await page.request.get(
    `/sessions/${sid}/nodes/3/export?format=csv`,
  );
  expect(alone.status()).toBe(422);
  const joined = await page.request.get(
    `/sessions/${sid}/nodes/4/export?format=csv`,
  );
  expect(joined.status()).toBe(200);
  expect(await joined.text()).toBe("id,city\r\n10,sp\r\n20,rj\r\n");
});

test("a correlated filter's form offers the row it reads, and keeps its column operand", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("correlation");
  await canvas.openEdit(3);
  const head = page.getByTestId("form").locator(".condition-head").first();
  await expect(head.getByTestId("condition-operand")).toHaveValue("column");
  const right = head.locator('[role="combobox"]').nth(1);
  await expect(right).toHaveValue("d.id");
  await right.fill("d.");
  const options = page.getByRole("listbox").getByRole("option");
  await expect(options.filter({ hasText: "d.city" })).toHaveCount(1);
  await expect(options.filter({ hasText: "d.id" })).toHaveCount(1);
});

test("a term-less nested-loop join cannot become a hash join", async ({
  page,
  bootFile,
}) => {
  const { canvas } = await bootFile("correlation");
  await canvas.openEdit(4);
  await page
    .getByTestId("form")
    .getByTestId("input-algorithm")
    .selectOption({ value: "HASH" });
  await page.getByTestId("form-save").click();
  await expect(page.getByTestId("form-error")).toContainText(
    "termo de igualdade",
  );
  await page.getByTestId("form-cancel").click();
  await canvas.run(4);
});

test("renaming the Condition and the AND it feeds keeps the answer", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("correlation");
  await canvas.rename(11, "maiorQueUm");
  await canvas.rename(13, "tudo");
  await canvas.run(14);
  expect((await results.readAll()).rows).toEqual([
    ["2", "bob", "20", "z"],
    ["3", "cid", "10", "w"],
  ]);
});

test("renaming a table a Reference reads renames the column the Reference names after it", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("correlation");
  await canvas.rename(15, "f");
  await expect(canvas.node(16).locator(".node-caption")).toHaveText("f.dept");
  await expect(canvas.node(28).locator(".node-caption")).toHaveText(
    "atual.f_dept = 10",
  );
  await canvas.run(28);
  expect(await results.readAll()).toEqual({
    schema: ["f.id", "f.name", "f.dept", "f.tags", "atual.f_dept"],
    rows: [
      ["1", "ana", "10", "x;y", "10"],
      ["3", "cid", "10", "w", "10"],
    ],
  });
});
