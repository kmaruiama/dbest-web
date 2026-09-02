import { expect, type Locator, type Page } from "@playwright/test";

export type NewColumn = {
  name: string;
  type?: string;
  primaryKey?: boolean;
};

export class TableModal {
  constructor(private readonly page: Page) {}
  get root(): Locator {
    return this.page.getByTestId("table-modal");
  }
  async open(): Promise<void> {
    await this.page.getByTestId("bottombar-new-table").click();
    await expect(this.root).toBeVisible();
  }
  async named(text: string): Promise<void> {
    await this.root.getByTestId("table-modal-name").fill(text);
  }
  async columns(list: NewColumn[]): Promise<void> {
    const names = this.root.getByTestId("table-modal-column-name");
    for (let extra = await names.count(); extra < list.length; extra++) {
      await this.root.getByTestId("table-modal-add-column").click();
    }
    for (const [index, column] of list.entries()) {
      await names.nth(index).fill(column.name);
      if (column.type !== undefined) {
        await this.root
          .getByTestId("table-modal-column-type")
          .nth(index)
          .selectOption(column.type);
      }
      if (column.primaryKey === true) {
        await this.root.getByTestId("table-modal-column-pk").nth(index).check();
      }
    }
  }
  async rows(grid: string[][]): Promise<void> {
    for (let added = 0; added < grid.length; added++) {
      await this.root.getByTestId("table-modal-add-row").click();
    }
    const cells = this.root.getByTestId("table-modal-cell");
    const width = grid[0]?.length ?? 0;
    for (const [rowAt, row] of grid.entries()) {
      for (const [columnAt, value] of row.entries()) {
        await cells.nth(rowAt * width + columnAt).fill(value);
      }
    }
  }
  async save(): Promise<void> {
    await this.root.getByTestId("table-modal-save").click();
    await expect(this.root).toBeHidden();
  }
}
