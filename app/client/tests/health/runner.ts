import { expect, type Page } from "@playwright/test";
import type { HealthCase, Step } from "./manifest";
import { Canvas } from "./pages/canvas";
import { chipFor, type Catalog } from "./pages/catalog";
import { Form } from "./pages/form";
import { Results } from "./pages/results";

export type Outcome =
  | {
      ok: true;
    }
  | {
      ok: false;
      why: string;
    };

const COLUMN = 330;
const ROW = 210;
const ORIGIN = { x: 170, y: 110 };

function layout(steps: Step[]): Map<
  string,
  {
    x: number;
    y: number;
  }
> {
  const depth = new Map<string, number>();
  for (const step of steps) {
    depth.set(
      step.as,
      "table" in step
        ? 0
        : 1 + Math.max(...step.inputs.map((input) => depth.get(input) ?? 0)),
    );
  }
  const used = new Map<number, number>();
  const spots = new Map<
    string,
    {
      x: number;
      y: number;
    }
  >();
  for (const step of steps) {
    const row = depth.get(step.as) ?? 0;
    const column = used.get(row) ?? 0;
    used.set(row, column + 1);
    spots.set(step.as, {
      x: ORIGIN.x + column * COLUMN,
      y: ORIGIN.y + row * ROW,
    });
  }
  return spots;
}

function render(
  row: string[],
  headers: string[],
  show: string[] | undefined,
): string {
  if (show === undefined) {
    return headers
      .map((header, index) => `${header}=${row[index] ?? ""}`)
      .join(" ");
  }
  return show
    .map((key) => {
      const index = headers.indexOf(key);
      return index === -1 ? "-" : row[index] || "-";
    })
    .join("/");
}

function same(
  produced: string[],
  expected: string[],
  ordered: boolean,
): boolean {
  const left = ordered ? produced : [...produced].sort();
  const right = ordered ? expected : [...expected].sort();
  return (
    left.length === right.length &&
    left.every((value, index) => value === right[index])
  );
}

export async function runCase(
  page: Page,
  entry: Catalog,
  test: HealthCase,
): Promise<Outcome> {
  const canvas = new Canvas(page);
  const form = new Form(page, entry);
  const results = new Results(page);
  const spots = layout(test.steps);
  const ids = new Map<string, number>();
  for (const step of test.steps) {
    const at = spots.get(step.as);
    if (at === undefined)
      throw new Error(`no layout spot for step "${step.as}"`);
    if ("table" in step) {
      ids.set(step.as, await canvas.addTable(step.table, step.alias, at));
      continue;
    }
    const id = await canvas.dropChip(step.chip, at);
    ids.set(step.as, id);
    const kind = chipFor(entry, step.chip).type;
    const ports =
      step.inputs.length === 2 ? (["LEFT", "RIGHT"] as const) : [undefined];
    for (const [index, input] of step.inputs.entries()) {
      const from = ids.get(input);
      if (from === undefined)
        throw new Error(`step "${step.as}" links unknown input "${input}"`);
      await canvas.link(from, id, ports[index]);
    }
    if ("chip" in step && entry.types[kind].editable)
      await canvas.expectAutoForm();
    if (step.edits !== undefined) {
      await canvas.openEdit(id);
      await form.fill(kind, step.edits);
      await form.save();
    } else {
      await canvas.dismissAutoForm();
    }
  }
  for (const step of test.steps) {
    await expect(canvas.node(ids.get(step.as) as number)).toBeVisible();
  }
  const subject = test.steps.find((step) => step.as === "subject");
  if (
    subject !== undefined &&
    !("table" in subject) &&
    subject.edits !== undefined
  ) {
    const id = ids.get("subject") as number;
    await canvas.openEdit(id);
    await form.check(chipFor(entry, subject.chip).type, subject.edits);
    await form.cancel();
  }
  const root = ids.get(test.root);
  if (root === undefined)
    throw new Error(`case names root "${test.root}", which is not a step`);
  await canvas.run(root);
  const failure = await results.failure();
  if (failure !== null) {
    await results.dismiss();
    return { ok: false, why: `the engine refused the plan: ${failure}` };
  }
  let read;
  try {
    read = await results.readAll();
  } catch (error) {
    await results.dismiss().catch(() => undefined);
    return {
      ok: false,
      why: String(error instanceof Error ? error.message : error),
    };
  }
  await results.dismiss();
  if (test.schema !== undefined && !same(read.schema, test.schema, true)) {
    return {
      ok: false,
      why: `schema was [${read.schema.join(", ")}], expected [${test.schema.join(", ")}]`,
    };
  }
  const produced = read.rows.map((row) => render(row, read.schema, test.show));
  if (!same(produced, test.expect, test.ordered === true)) {
    return {
      ok: false,
      why: `tuples were [${produced.join(", ")}], expected [${test.expect.join(", ")}]`,
    };
  }
  return { ok: true };
}
