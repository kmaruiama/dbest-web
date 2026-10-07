import {
  test as base,
  expect,
  type APIRequestContext,
  type Page,
} from "@playwright/test";
import { BACKEND_URL } from "./env";
import type { FixtureName } from "./fixtures";
import { Canvas } from "./pages/canvas";
import { catalog, type Catalog } from "./pages/catalog";
import { Form } from "./pages/form";
import { Results } from "./pages/results";
import {
  closeAllSessions,
  closeSession,
  openSession,
  openSessionFile,
  type Session,
} from "./pages/session";

type Pages = {
  canvas: Canvas;
  form: Form;
  results: Results;
};

export type Boot = (tables: FixtureName[]) => Promise<Pages>;

export type BootFile = (file: string) => Promise<Pages>;

type Fixtures = {
  boot: Boot;
  bootFile: BootFile;
  entry: Catalog;
  consoleErrors: string[];
};

async function seedSettings(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("dbest.lang", "en-US");
    localStorage.setItem("dbest.theme", "light");
    localStorage.setItem("dbest.palette.visible", "true");
    localStorage.setItem("dbest.caption.expression", "true");
  });
}

export const test = base.extend<Fixtures>({
  entry: async ({ request }, use) => {
    await use(await catalog(request));
  },
  consoleErrors: async ({ page }, use) => {
    const errors: string[] = [];
    page.on("console", (message) => {
      if (message.type() !== "error") return;
      if (message.location().url.endsWith("/favicon.ico")) return;
      errors.push(message.text());
    });
    page.on("pageerror", (error) => errors.push(String(error)));
    await use(errors);
  },
  boot: async ({ page, request, entry, consoleErrors }, use, info) => {
    void consoleErrors;
    const opened: string[] = [];
    await use((tables) =>
      start(page, request, entry, opened, () =>
        openSession(request, `health-${info.testId}-${info.retry}`, tables),
      ),
    );
    for (const sid of opened) await closeSession(request, sid);
  },
  bootFile: async ({ page, request, entry, consoleErrors }, use, info) => {
    void consoleErrors;
    const opened: string[] = [];
    await use((file) =>
      start(page, request, entry, opened, () =>
        openSessionFile(request, file, `${file}-${info.testId}-${info.retry}`),
      ),
    );
    for (const sid of opened) await closeSession(request, sid);
  },
});

async function start(
  page: Page,
  request: APIRequestContext,
  entry: Catalog,
  opened: string[],
  open: () => Promise<Session>,
): Promise<Pages> {
  await closeAllSessions(request);
  await expect
    .poll(
      async () =>
        (
          (await (
            await request.get(`${BACKEND_URL}/sessions`)
          ).json()) as unknown[]
        ).length,
    )
    .toBe(0);
  const session = await open();
  opened.push(session.sid);
  await seedSettings(page);
  await page.goto("/");
  await expect(page.locator(".flow")).toBeVisible();
  await expect(page.getByTestId("rf__wrapper")).toBeVisible();
  return {
    canvas: new Canvas(page),
    form: new Form(page, entry),
    results: new Results(page),
  };
}

export { expect };
