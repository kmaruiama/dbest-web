import type { Request } from "@playwright/test";
import { expect, test } from "./harness";

test("running a node asks the server once for its first page and once for all rows", async ({
  page,
  boot,
}) => {
  const { canvas } = await boot(["emp"]);
  const emp = await canvas.addTable("emp", "emp", { x: 160, y: 140 });
  const asked: string[] = [];
  const track = (request: Request) => {
    const path = new URL(request.url()).pathname;
    if (path.endsWith(`/nodes/${emp}/rows`)) asked.push(request.url());
  };
  page.on("request", track);
  await canvas.run(emp);
  await expect(page.getByTestId("results-table")).toBeVisible();
  await page.getByTestId("results-all").click();
  await expect(page.getByTestId("results-total")).not.toContainText("?");
  await page.waitForTimeout(300);
  page.off("request", track);
  expect(asked.filter((url) => url.includes("offset=0"))).toHaveLength(1);
  expect(asked.filter((url) => !url.includes("offset="))).toHaveLength(1);
});
