import { expect, test } from "./harness";

test("a version 2 file opens, and its term-less hash join asks for a term", async ({
  page,
  bootFile,
}) => {
  const { canvas, form, results } = await bootFile("legacy-v2");
  await expect.poll(() => canvas.problem(3)).toContain("not found");
  await canvas.openEdit(3);
  const term = page.getByTestId("form").getByTestId("field-on");
  await expect(term.locator('[role="combobox"]').first()).toHaveValue("");
  await form.fill("join", [
    { at: "on", rows: [{ left: "e.dept", right: "d.id" }] },
  ]);
  await form.save();
  await expect.poll(() => canvas.problem(3)).toBeNull();
  await canvas.run(3);
  expect((await results.readAll()).rows).toEqual([
    ["1", "ana", "10", "x;y", "10", "sp"],
    ["2", "bob", "20", "z", "20", "rj"],
    ["3", "cid", "10", "w", "10", "sp"],
  ]);
});

test("an old union is named after its left input until it is renamed", async ({
  bootFile,
}) => {
  const { canvas, results } = await bootFile("legacy-v2");
  await canvas.run(8);
  expect(await results.readAll()).toEqual({
    schema: ["e.id", "e.name", "e.dept", "e.tags"],
    rows: [
      ["1", "ana", "10", "x;y"],
      ["3", "cid", "10", "w"],
      ["4", "dan", "40", "q"],
    ],
  });
  await results.dismiss();
  await canvas.rename(8, "velhos");
  await canvas.run(8);
  expect((await results.readAll()).schema[0]).toBe("velhos.id");
  await results.dismiss();
  await canvas.run(11);
  expect((await results.readAll()).rows).toHaveLength(3);
});
