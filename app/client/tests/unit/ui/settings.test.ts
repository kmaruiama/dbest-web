import { afterEach, describe, expect, it, vi } from "vitest";

function memoryStorage(entries: Record<string, string>) {
  const values = new Map(Object.entries(entries));
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => void values.set(key, value),
    removeItem: (key: string) => void values.delete(key),
  };
}

describe("settings", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("loads with stored palette preferences", async () => {
    const stored = {
      "dbest.palette.width": "1000",
      "dbest.palette.collapsed-categories": '["Joins"]',
    };
    vi.stubGlobal("localStorage", memoryStorage(stored));
    const settings = await import("../../../src/ui/settings");
    expect(settings.clampWidth(1000)).toBe(620);
    expect(settings.useSettings).toBeTypeOf("function");
  });
});
