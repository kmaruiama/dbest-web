import { describe, expect, it } from "vitest";
import { humanize, isLang, label, translate } from "../../../src/i18n/core";
import type { Params } from "../../../src/i18n/core";
import type { Lang } from "../../../src/i18n/strings";

describe("humanize", () => {
  const scenarios: { name: string; identifier: string; readable: string }[] = [
    {
      name: "camelCase splits into words",
      identifier: "engineClass",
      readable: "engine class",
    },
    {
      name: "snake_case splits into words",
      identifier: "created_at",
      readable: "created at",
    },
    {
      name: "a digit before a capital is a boundary",
      identifier: "row2Col",
      readable: "row2 col",
    },
    {
      name: "an already-lowercase word is left alone",
      identifier: "total",
      readable: "total",
    },
  ];

  it.each(scenarios)("$name", ({ identifier, readable }) => {
    expect(humanize(identifier)).toBe(readable);
  });
});

describe("translate", () => {
  const scenarios: {
    name: string;
    lang: Lang;
    key: string;
    params?: Params;
    text: string;
  }[] = [
    {
      name: "fills a placeholder from params",
      lang: "en-US",
      key: "rowTotal",
      params: { n: 5 },
      text: "5 tuples",
    },
    {
      name: "reads the requested language",
      lang: "pt-BR",
      key: "rowTotal",
      params: { n: 5 },
      text: "5 tuplas",
    },
    {
      name: "leaves the placeholder when params are absent",
      lang: "en-US",
      key: "rowTotal",
      text: "{n} tuples",
    },
    {
      name: "echoes an unknown key",
      lang: "en-US",
      key: "no.such.key",
      text: "no.such.key",
    },
  ];

  it.each(scenarios)("$name", ({ lang, key, params, text }) => {
    expect(translate(lang, key, params)).toBe(text);
  });
});

describe("label", () => {
  const scenarios: {
    name: string;
    key: string;
    fallback: string;
    text: string;
  }[] = [
    {
      name: "a known key ignores the fallback",
      key: "retry",
      fallback: "RETRY",
      text: "retry",
    },
    {
      name: "an unknown key uses the fallback",
      key: "op.madeUp",
      fallback: "made up",
      text: "made up",
    },
  ];

  it.each(scenarios)("$name", ({ key, fallback, text }) => {
    expect(label("en-US", key, fallback)).toBe(text);
  });
});

describe("isLang", () => {
  const scenarios: { name: string; input: string; valid: boolean }[] = [
    { name: "the default language", input: "pt-BR", valid: true },
    { name: "the other language", input: "en-US", valid: true },
    { name: "a bare language code", input: "en", valid: false },
    { name: "nonsense", input: "klingon", valid: false },
  ];

  it.each(scenarios)("$name", ({ input, valid }) => {
    expect(isLang(input)).toBe(valid);
  });
});
