import { expect, type Locator, type Page } from "@playwright/test";
import { TABLE_IDS, type FixtureName } from "../fixtures";

export type Port = "LEFT" | "RIGHT";

export type Spot = {
  x: number;
  y: number;
};

export class Canvas {
  constructor(private readonly page: Page) {}
  node(id: number): Locator {
    return this.page.getByTestId(`rf__node-${id}`);
  }
  edge(from: number, to: number, port: string): Locator {
    return this.page.getByTestId(`rf__edge-${from}-${to}-${port}`);
  }
  async nodeIds(): Promise<number[]> {
    const ids = await this.page
      .locator(".react-flow__node")
      .evaluateAll((nodes) =>
        nodes.map((node) => Number(node.getAttribute("data-id"))),
      );
    return ids.sort((a, b) => a - b);
  }
  private async dragTo(sourceTestId: string, at: Spot): Promise<void> {
    const flow = this.page.locator(".flow");
    const box = await flow.boundingBox();
    if (box === null) throw new Error("canvas is not visible");
    await this.page.evaluate(
      ({ sourceTestId: source, clientX, clientY }) => {
        const chip = document.querySelector(`[data-testid="${source}"]`);
        const target = document.querySelector(".flow");
        if (chip === null || target === null)
          throw new Error(`missing ${source} or .flow`);
        const transfer = new DataTransfer();
        const fire = (element: Element, type: string) =>
          element.dispatchEvent(
            new DragEvent(type, {
              bubbles: true,
              cancelable: true,
              dataTransfer: transfer,
              clientX,
              clientY,
            }),
          );
        fire(chip, "dragstart");
        fire(target, "dragover");
        fire(target, "drop");
      },
      { sourceTestId, clientX: box.x + at.x, clientY: box.y + at.y },
    );
  }
  private async added(action: () => Promise<void>): Promise<number> {
    const before = new Set(await this.nodeIds());
    await action();
    let fresh: number[] = [];
    await expect
      .poll(
        async () => {
          fresh = (await this.nodeIds()).filter((id) => !before.has(id));
          return fresh.length;
        },
        { message: "expected exactly one new node on the canvas" },
      )
      .toBe(1);
    return fresh[0];
  }
  async dropChip(chip: string, at: Spot): Promise<number> {
    return this.added(() => this.dragTo(`chip-${chip}`, at));
  }
  async addTable(table: FixtureName, alias: string, at: Spot): Promise<number> {
    return this.added(async () => {
      await this.dragTo(`table-${TABLE_IDS[table]}`, at);
      const input = this.page.getByTestId("alias-input");
      await expect(input).toBeVisible();
      await input.fill(alias);
      await this.page.getByTestId("alias-confirm").click();
      await expect(input).toBeHidden();
    });
  }
  async select(id: number): Promise<void> {
    await this.node(id).locator(".node-symbol").click({ button: "right" });
    await expect(this.node(id).getByTestId("node-tools")).toBeVisible();
  }
  async link(from: number, to: number, port?: Port): Promise<void> {
    await this.select(from);
    await this.node(from).getByTestId("node-link").click();
    await expect(this.node(to).locator(".node-box")).not.toHaveClass(/dimmed/);
    await this.node(to).locator(".node-symbol").click();
    const edge = this.edge(from, to, port ?? "ONLY");
    if (port !== undefined) {
      const choice = this.page.getByTestId(`port-${port}`);
      await expect(choice.or(edge).first()).toBeAttached();
      if (await choice.isVisible()) await choice.click();
    }
    await expect(edge).toBeAttached();
  }
  async openEdit(id: number): Promise<void> {
    const form = this.page.getByTestId("form");
    if (await form.isVisible()) return;
    await this.select(id);
    await this.node(id).getByTestId("node-edit").click();
    await expect(form).toBeVisible();
  }
  async expectAutoForm(): Promise<void> {
    await expect(this.page.getByTestId("form")).toBeVisible();
  }
  async dismissAutoForm(): Promise<void> {
    const form = this.page.getByTestId("form");
    if (await form.isVisible()) {
      await this.page.getByTestId("form-cancel").click();
      await expect(form).toBeHidden();
    }
  }
  async run(id: number): Promise<void> {
    await this.select(id);
    await this.node(id).getByTestId("node-run").click();
    await expect(this.page.getByTestId("results")).toBeVisible();
  }
  async problem(id: number): Promise<string | null> {
    return this.node(id).locator(".node-box").getAttribute("title");
  }
  async hasEditButton(id: number): Promise<boolean> {
    await this.select(id);
    return (await this.node(id).getByTestId("node-edit").count()) > 0;
  }
}
