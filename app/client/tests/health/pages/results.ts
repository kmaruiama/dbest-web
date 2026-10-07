import { expect, type Locator, type Page } from "@playwright/test";

export type Tuples = {
  schema: string[];
  rows: string[][];
};

export class Results {
  constructor(private readonly page: Page) {}
  get root(): Locator {
    return this.page.getByTestId("results");
  }
  private get table(): Locator {
    return this.root.getByTestId("results-table");
  }
  async failure(): Promise<string | null> {
    const error = this.root.getByTestId("results-body").locator(".centred p");
    await expect(this.table.or(error)).toBeVisible();
    return (await error.count()) === 0 ? null : error.first().innerText();
  }
  async readAll(): Promise<Tuples> {
    await expect(
      this.table.or(this.root.getByTestId("results-body").locator(".centred")),
    ).toBeVisible();
    if ((await this.total()).includes("?")) {
      await this.root.getByTestId("results-all").click();
      await expect
        .poll(async () => (await this.total()).includes("?"), {
          message: "row total stayed partial",
        })
        .toBe(false);
    }
    const schema = await this.table.locator("thead th").allInnerTexts();
    if (
      schema.length === 0 ||
      schema.some((header, index) => header === String(index))
    ) {
      throw new Error(
        `the schema fetch failed and was swallowed; headers read [${schema.join(", ")}]`,
      );
    }
    const rows = await this.table
      .locator("tbody tr")
      .evaluateAll((trs) =>
        trs.map((tr) =>
          [...tr.querySelectorAll("td")].map((td) =>
            (td.textContent ?? "").trim(),
          ),
        ),
      );
    return { schema, rows: rows.map((row) => row.map(String)) };
  }
  async total(): Promise<string> {
    return this.root.getByTestId("results-total").innerText();
  }
  async pageLabel(): Promise<string> {
    return this.root.getByTestId("results-page").innerText();
  }
  async dismiss(): Promise<void> {
    await this.root.getByTestId("results-dismiss").click();
    await expect(this.root).toBeHidden();
  }
}
