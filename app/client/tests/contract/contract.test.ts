import { afterAll, beforeAll, expect, it } from "vitest";
import { run as catalog } from "./catalog";
import { type Backend, startBackend } from "./harness";
import { run as operators } from "./operators";
import { run as routes } from "./routes";

let backend: Backend;

beforeAll(async () => {
  backend = await startBackend();
}, 180_000);

afterAll(() => backend?.stop());

it("every operator the palette serves compiles and runs", async () => {
  expect(await operators(backend.base)).toBe(0);
});

it("every route the client depends on behaves end to end", async () => {
  expect(await routes(backend.base)).toBe(0);
});

it("the operator catalog is self-consistent and drives the client", async () => {
  expect(await catalog(backend.base)).toBe(0);
});
