import { describe, expect, it } from "vitest";
import {
  acceptsLiteral,
  columnLiteral,
  columnTypeOf,
  readColumnRef,
  readLiteral,
  readQualified,
  showColumnRef,
  showLiteral,
  showQualified,
  showRawValue,
  type ColumnType,
} from "../../../src/server/literals";
import type { ColumnReference, Literal } from "../../../src/server/types";

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

describe("readQualified", () => {
  const scenarios: {
    name: string;
    text: string;
    nullable: boolean;
    parsed: { source: string; column: string } | null;
  }[] = [
    {
      name: "empty and nullable is null",
      text: "",
      nullable: true,
      parsed: null,
    },
    {
      name: "whitespace and nullable is null",
      text: "  ",
      nullable: true,
      parsed: null,
    },
    {
      name: "empty and not nullable is a blank pair",
      text: "",
      nullable: false,
      parsed: { source: "", column: "" },
    },
    {
      name: "source.column splits on the first dot",
      text: "o.name",
      nullable: false,
      parsed: { source: "o", column: "name" },
    },
    {
      name: "no dot means no source",
      text: "name",
      nullable: false,
      parsed: { source: "", column: "name" },
    },
    {
      name: "surrounding whitespace is trimmed",
      text: "  a.b  ",
      nullable: false,
      parsed: { source: "a", column: "b" },
    },
    {
      name: "only the first dot splits",
      text: "a.b.c",
      nullable: false,
      parsed: { source: "a", column: "b.c" },
    },
  ];
  it.each(scenarios)("$name", ({ text, nullable, parsed }) => {
    expect(readQualified(text, nullable)).toEqual(parsed);
  });
});

describe("readColumnRef", () => {
  const scenarios: { name: string; text: string; ref: ColumnReference }[] = [
    {
      name: "no dot omits the source key",
      text: "name",
      ref: { name: "name" },
    },
    {
      name: "a dot fills the source",
      text: "o.name",
      ref: { source: "o", name: "name" },
    },
  ];
  it.each(scenarios)("$name", ({ text, ref }) => {
    expect(readColumnRef(text)).toEqual(ref);
  });
});

describe("showColumnRef", () => {
  const scenarios: { name: string; ref: ColumnReference; text: string }[] = [
    {
      name: "a sourceless ref is its bare name",
      ref: { name: "n" },
      text: "n",
    },
    {
      name: "a sourced ref is joined by a dot",
      ref: { source: "o", name: "n" },
      text: "o.n",
    },
  ];
  it.each(scenarios)("$name", ({ ref, text }) => {
    expect(showColumnRef(ref)).toBe(text);
  });
});

describe("column references round-trip", () => {
  it.each(["name", "o.name", "a.b.c"])("%s survives read then show", (text) => {
    expect(showColumnRef(readColumnRef(text))).toBe(text);
  });
});

describe("showQualified", () => {
  const scenarios: {
    name: string;
    value: { source?: string; column: string } | null | undefined;
    text: string;
  }[] = [
    { name: "null is blank", value: null, text: "" },
    { name: "undefined is blank", value: undefined, text: "" },
    {
      name: "an empty pair is blank",
      value: { source: "", column: "" },
      text: "",
    },
    {
      name: "a full pair joins on a dot",
      value: { source: "o", column: "x" },
      text: "o.x",
    },
    {
      name: "a missing source shows as a leading dot",
      value: { column: "x" },
      text: ".x",
    },
  ];
  it.each(scenarios)("$name", ({ value, text }) => {
    expect(showQualified(value)).toBe(text);
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
