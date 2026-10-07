import { describe, expect, it } from "vitest";
import {
  acceptsLiteral,
  columnLiteral,
  columnTypeOf,
  readLiteral,
  showLiteral,
  showRawValue,
} from "../../../src/server/literals";
import type { ColumnType, Literal } from "../../../src/server/types";

describe("readLiteral", () => {
  const scenarios: { name: string; text: string; literal: Literal }[] = [
    { name: "a bare integer", text: "42", literal: { int: 42 } },
    { name: "a negative integer", text: "-3", literal: { int: -3 } },
    {
      name: "surrounding whitespace is trimmed",
      text: "  7  ",
      literal: { int: 7 },
    },
    { name: "a decimal", text: "3.14", literal: { double: 3.14 } },
    { name: "a leading-dot decimal", text: ".5", literal: { double: 0.5 } },
    { name: "a trailing-dot decimal", text: "1.", literal: { double: 1 } },
    { name: "scientific notation", text: "1e5", literal: { double: 100000 } },
    { name: "a lowercase boolean", text: "true", literal: { bool: true } },
    { name: "an uppercase boolean", text: "TRUE", literal: { bool: true } },
    { name: "a mixed-case boolean", text: "False", literal: { bool: false } },
    {
      name: "a plain word is a string",
      text: "hello",
      literal: { str: "hello" },
    },
    {
      name: "a grouped number stays a string",
      text: "1,000",
      literal: { str: "1,000" },
    },
    { name: "the empty string", text: "", literal: { str: "" } },
  ];
  it.each(scenarios)("$name", ({ text, literal }) => {
    expect(readLiteral(text)).toEqual(literal);
  });
});

describe("columnTypeOf", () => {
  const scenarios: { name: string; sample: string; type: ColumnType }[] = [
    { name: "an integer sample", sample: "5", type: "INT" },
    { name: "a decimal sample", sample: "5.5", type: "DOUBLE" },
    { name: "a boolean sample", sample: "true", type: "BOOLEAN" },
    { name: "anything else", sample: "x", type: "STRING" },
  ];
  it.each(scenarios)("$name", ({ sample, type }) => {
    expect(columnTypeOf(sample)).toBe(type);
  });
});

describe("acceptsLiteral", () => {
  const scenarios: {
    name: string;
    type: ColumnType;
    text: string;
    ok: boolean;
  }[] = [
    { name: "INT takes blank text", type: "INT", text: "", ok: true },
    { name: "INT takes an integer", type: "INT", text: "5", ok: true },
    { name: "INT rejects a decimal", type: "INT", text: "5.5", ok: false },
    { name: "INT rejects a word", type: "INT", text: "abc", ok: false },
    { name: "FLOAT takes an integer", type: "FLOAT", text: "5", ok: true },
    { name: "FLOAT takes a decimal", type: "FLOAT", text: "5.5", ok: true },
    { name: "DOUBLE takes an integer", type: "DOUBLE", text: "5", ok: true },
    {
      name: "BOOLEAN takes a boolean",
      type: "BOOLEAN",
      text: "true",
      ok: true,
    },
    { name: "BOOLEAN rejects a number", type: "BOOLEAN", text: "1", ok: false },
    { name: "STRING takes a number", type: "STRING", text: "5", ok: true },
    {
      name: "STRING takes any word",
      type: "STRING",
      text: "anything",
      ok: true,
    },
  ];
  it.each(scenarios)("$name", ({ type, text, ok }) => {
    expect(acceptsLiteral(type, text)).toBe(ok);
  });
});

describe("columnLiteral", () => {
  const scenarios: {
    name: string;
    type: ColumnType;
    text: string;
    literal: Literal | null;
  }[] = [
    { name: "blank text is null", type: "INT", text: "", literal: null },
    { name: "whitespace is null", type: "STRING", text: "   ", literal: null },
    {
      name: "tags by the column, not by inference",
      type: "DOUBLE",
      text: "5",
      literal: { double: 5 },
    },
    {
      name: "a number under a STRING column stays a string",
      type: "STRING",
      text: "5",
      literal: { str: "5" },
    },
    { name: "a LONG column", type: "LONG", text: "9", literal: { long: 9 } },
  ];
  it.each(scenarios)("$name", ({ type, text, literal }) => {
    expect(columnLiteral(type, text)).toEqual(literal);
  });
});

describe("showLiteral", () => {
  const scenarios: { name: string; value: Literal; text: string }[] = [
    { name: "an int", value: { int: 5 }, text: "5" },
    { name: "a string", value: { str: "hi" }, text: "hi" },
    { name: "a boolean", value: { bool: true }, text: "true" },
  ];
  it.each(scenarios)("$name", ({ value, text }) => {
    expect(showLiteral(value)).toBe(text);
  });
});

describe("showRawValue", () => {
  const scenarios: { name: string; value: unknown; text: string }[] = [
    { name: "null is blank", value: null, text: "" },
    { name: "undefined is blank", value: undefined, text: "" },
    { name: "zero is kept", value: 0, text: "0" },
    { name: "false is kept", value: false, text: "false" },
    { name: "a string passes through", value: "x", text: "x" },
  ];
  it.each(scenarios)("$name", ({ value, text }) => {
    expect(showRawValue(value)).toBe(text);
  });
});
