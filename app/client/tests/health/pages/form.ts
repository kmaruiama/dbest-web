import { expect, type Locator, type Page } from "@playwright/test";
import { fieldsOf, specFor, type Catalog, type FieldSpec } from "./catalog";

export type ConditionEdit =
  | {
      left: string;
      op: string;
      right: string;
    }
  | {
      shape: "isNull" | "isNotNull";
      column: string;
    }
  | {
      all: ConditionEdit[];
    }
  | {
      any: ConditionEdit[];
    };

export type Edit =
  | {
      at: string;
      text: string;
    }
  | {
      at: string;
      int: number;
    }
  | {
      at: string;
      flag: boolean;
    }
  | {
      at: string;
      pick: string;
    }
  | {
      at: string;
      value: string;
    }
  | {
      at: string;
      list: string[];
    }
  | {
      at: string;
      rows: Record<string, string | boolean>[];
    }
  | {
      at: string;
      condition: ConditionEdit;
    };

const COMBOBOX_WIDGETS = new Set(["column", "qualified"]);

export class Form {
  constructor(
    private readonly page: Page,
    private readonly entry: Catalog,
  ) {}
  get root(): Locator {
    return this.page.getByTestId("form");
  }
  private field(at: string): Locator {
    return this.root.getByTestId(`field-${at}`);
  }
  private combobox(scope: Locator, index = 0): Locator {
    return scope.locator('[role="combobox"]').nth(index);
  }
  private async setCombobox(box: Locator, text: string): Promise<void> {
    await box.fill(text);
    await box.blur();
    await expect(box).toHaveValue(text);
  }
  async fill(kind: string, edits: Edit[]): Promise<void> {
    for (const edit of edits) await this.apply(kind, edit);
  }
  private async apply(kind: string, edit: Edit): Promise<void> {
    const scope = this.field(edit.at);
    await expect(
      scope,
      `form for ${kind} has no field "${edit.at}"`,
    ).toBeVisible();
    if ("text" in edit) return this.setPlain(scope, edit.at, edit.text);
    if ("value" in edit) return this.setPlain(scope, edit.at, edit.value);
    if ("int" in edit) return this.setPlain(scope, edit.at, String(edit.int));
    if ("pick" in edit) {
      await scope
        .getByTestId(`input-${edit.at}`)
        .selectOption({ value: edit.pick });
      return;
    }
    if ("flag" in edit) {
      const box = scope.getByTestId(`input-${edit.at}`);
      if (await box.isDisabled()) {
        expect(
          await box.isChecked(),
          `${edit.at} is settled at the other value`,
        ).toBe(edit.flag);
        return;
      }
      await box.setChecked(edit.flag);
      return;
    }
    if ("list" in edit) return this.setList(kind, scope, edit.at, edit.list);
    if ("rows" in edit) return this.setRows(kind, scope, edit.at, edit.rows);
    return this.setCondition(
      scope.locator(".condition").first(),
      edit.condition,
    );
  }
  private async setPlain(
    scope: Locator,
    at: string,
    text: string,
  ): Promise<void> {
    const direct = scope.getByTestId(`input-${at}`);
    if ((await direct.count()) > 0) {
      await direct.fill(text);
      return;
    }
    await this.setCombobox(this.combobox(scope), text);
  }
  private async resize(scope: Locator, count: number): Promise<void> {
    const rows = scope.getByTestId("row");
    let have = await rows.count();
    while (have < count) {
      await scope.getByTestId("add-row").click();
      await expect(rows).toHaveCount(have + 1);
      have = have + 1;
    }
    while (have > count) {
      await rows
        .nth(have - 1)
        .locator(".row-remove")
        .click();
      await expect(rows).toHaveCount(have - 1);
      have = have - 1;
    }
  }
  private async setList(
    kind: string,
    scope: Locator,
    at: string,
    values: string[],
  ): Promise<void> {
    await this.resize(scope, values.length);
    const item = specFor(this.entry, kind, at)?.item;
    for (const [index, value] of values.entries()) {
      const row = scope.getByTestId("row").nth(index);
      if (item !== undefined && COMBOBOX_WIDGETS.has(item)) {
        await this.setCombobox(this.combobox(row), value);
      } else {
        await row.getByTestId(`input-${at}`).fill(value);
      }
    }
  }
  private async setRows(
    kind: string,
    scope: Locator,
    at: string,
    entries: Record<string, string | boolean>[],
  ): Promise<void> {
    await this.resize(scope, entries.length);
    const cells: FieldSpec[] = specFor(this.entry, kind, at)?.of ?? [];
    const boxOrder = cells
      .filter((cell) => COMBOBOX_WIDGETS.has(cell.widget))
      .map((cell) => cell.at);
    for (const [index, entry] of entries.entries()) {
      const row = scope.getByTestId("row").nth(index);
      for (const [cellAt, value] of Object.entries(entry)) {
        const cell = cells.find((option) => option.at === cellAt);
        if (cell === undefined)
          throw new Error(`${kind}.${at} has no cell "${cellAt}"`);
        if (COMBOBOX_WIDGETS.has(cell.widget)) {
          await this.setCombobox(
            this.combobox(row, boxOrder.indexOf(cellAt)),
            String(value),
          );
          continue;
        }
        const control = row.getByTestId(`input-${cellAt}`);
        if (cell.widget === "flag") await control.setChecked(value === true);
        else if (cell.widget === "pick")
          await control.selectOption({ value: String(value) });
        else await control.fill(String(value));
      }
    }
  }
  private async setCondition(
    scope: Locator,
    edit: ConditionEdit,
  ): Promise<void> {
    const shape =
      "all" in edit
        ? "all"
        : "any" in edit
          ? "any"
          : "shape" in edit
            ? edit.shape === "isNull"
              ? "nullCheck"
              : "notNull"
            : "comparison";
    await scope
      .getByTestId("condition-shape")
      .first()
      .selectOption({ value: shape });
    const head = scope.locator(".condition-head").first();
    if ("left" in edit) {
      await this.setCombobox(this.combobox(head), edit.left);
      await head.getByTestId("condition-op").selectOption({ value: edit.op });
      await head.getByTestId("condition-right").fill(edit.right);
      return;
    }
    if ("column" in edit) {
      await this.setCombobox(this.combobox(head), edit.column);
      return;
    }
    const children = "all" in edit ? edit.all : edit.any;
    const nested = scope.locator(".condition-children").first();
    let have = await nested.locator("> .condition").count();
    while (have < children.length) {
      await nested.getByTestId("condition-add").first().click();
      await expect(nested.locator("> .condition")).toHaveCount(have + 1);
      have = have + 1;
    }
    for (const [index, child] of children.entries()) {
      await this.setCondition(nested.locator("> .condition").nth(index), child);
    }
  }
  async save(): Promise<void> {
    const button = this.page.getByTestId("form-save");
    await expect(button).toBeEnabled();
    await button.click();
    await expect(this.root).toBeHidden();
  }
  async cancel(): Promise<void> {
    await this.page.getByTestId("form-cancel").click();
    await expect(this.root).toBeHidden();
  }
  async check(kind: string, edits: Edit[]): Promise<void> {
    for (const edit of edits) {
      const scope = this.field(edit.at);
      if ("pick" in edit) {
        await expect(scope.getByTestId(`input-${edit.at}`)).toHaveValue(
          edit.pick,
        );
      } else if ("flag" in edit) {
        const box = scope.getByTestId(`input-${edit.at}`);
        expect(await box.isChecked(), `${kind}.${edit.at} lost its flag`).toBe(
          edit.flag,
        );
      } else if ("int" in edit) {
        await expect(scope.getByTestId(`input-${edit.at}`)).toHaveValue(
          String(edit.int),
        );
      } else if ("text" in edit || "value" in edit) {
        const wanted = "text" in edit ? edit.text : edit.value;
        const direct = scope.getByTestId(`input-${edit.at}`);
        const control =
          (await direct.count()) > 0 ? direct : this.combobox(scope);
        await expect(control).toHaveValue(wanted);
      } else if ("list" in edit) {
        await expect(scope.getByTestId("row")).toHaveCount(edit.list.length);
        const item = specFor(this.entry, kind, edit.at)?.item;
        for (const [index, value] of edit.list.entries()) {
          const row = scope.getByTestId("row").nth(index);
          const control =
            item !== undefined && COMBOBOX_WIDGETS.has(item)
              ? this.combobox(row)
              : row.getByTestId(`input-${edit.at}`);
          await expect(control).toHaveValue(value);
        }
      } else if ("rows" in edit) {
        await expect(scope.getByTestId("row")).toHaveCount(edit.rows.length);
        const cells: FieldSpec[] = specFor(this.entry, kind, edit.at)?.of ?? [];
        const boxOrder = cells
          .filter((cell) => COMBOBOX_WIDGETS.has(cell.widget))
          .map((cell) => cell.at);
        for (const [index, entry] of edit.rows.entries()) {
          const row = scope.getByTestId("row").nth(index);
          for (const [cellAt, value] of Object.entries(entry)) {
            const cell = cells.find((option) => option.at === cellAt);
            if (cell === undefined) continue;
            if (COMBOBOX_WIDGETS.has(cell.widget)) {
              await expect(
                this.combobox(row, boxOrder.indexOf(cellAt)),
              ).toHaveValue(String(value));
            } else if (cell.widget === "flag") {
              expect(await row.getByTestId(`input-${cellAt}`).isChecked()).toBe(
                value === true,
              );
            } else {
              await expect(row.getByTestId(`input-${cellAt}`)).toHaveValue(
                String(value),
              );
            }
          }
        }
      }
    }
  }
  fieldNames(kind: string): string[] {
    return fieldsOf(this.entry, kind).map((field) => field.at);
  }
}
