import { describe, expect, it } from "vitest";
import {
  blankColumn,
  guessColumnType,
  initial,
  specOf,
  step,
  type Event,
  type State,
} from "../../../src/tables/tables";
import type {
  Column,
  ColumnType,
  CsvTable,
  XmlTable,
} from "../../../src/server/types";

const col = (name: string, type: ColumnType): Column => ({
  name,
  type,
  primaryKey: false,
  nullable: true,
});

describe("guessColumnType", () => {
  const scenarios: {
    name: string;
    index: number;
    rows: (string | null)[][];
    type: ColumnType;
  }[] = [
    {
      name: "the majority type wins",
      index: 0,
      rows: [["5"], ["6"], ["x"]],
      type: "INT",
    },
    {
      name: "a majority the other way wins too",
      index: 0,
      rows: [["5"], ["x"], ["y"]],
      type: "STRING",
    },
    {
      name: "no usable samples falls back to STRING",
      index: 0,
      rows: [],
      type: "STRING",
    },
    {
      name: "blank and missing cells are ignored",
      index: 0,
      rows: [[""], ["  "], [null]],
      type: "STRING",
    },
    {
      name: "a tie goes to the type seen first (int)",
      index: 0,
      rows: [["5"], ["x"]],
      type: "INT",
    },
    {
      name: "a tie goes to the type seen first (string)",
      index: 0,
      rows: [["x"], ["5"]],
      type: "STRING",
    },
    {
      name: "reads the requested column, not the first",
      index: 1,
      rows: [
        ["x", "5"],
        ["y", "6"],
      ],
      type: "INT",
    },
  ];
  it.each(scenarios)("$name", ({ index, rows, type }) => {
    expect(guessColumnType(index, rows)).toBe(type);
  });
});

describe("initial", () => {
  it("starts with one blank column, nothing picked, and no error", () => {
    expect(initial("import").state).toEqual({
      tab: "import",
      name: "",
      columns: [blankColumn()],
      rows: [],
      picked: { kind: "none" },
      sampleRows: [],
      previewing: false,
      error: null,
      asked: 0,
    });
  });
});

describe("step", () => {
  const base = initial("create").state;
  const twoColumns: State = {
    ...base,
    columns: [blankColumn(), blankColumn()],
  };
  const csvSpec: CsvTable = {
    "@type": "csv",
    name: "t",
    path: "/t.csv",
    separator: ",",
    delimiter: '"',
    headerLine: 1,
    columns: [],
  };
  const xmlSpec: XmlTable = {
    "@type": "xml",
    name: "t",
    path: "/t.xml",
    rootElement: "",
    recordElement: "",
    columns: [],
  };
  const withCsv: State = {
    ...base,
    picked: { kind: "spec", spec: csvSpec },
  };
  const withXml: State = {
    ...base,
    picked: { kind: "spec", spec: xmlSpec },
  };

  const scenarios: {
    name: string;
    from: State;
    action: Event;
    to: State;
  }[] = [
    {
      name: "tabClicked switches the active tab",
      from: base,
      action: { kind: "tabClicked", tab: "import" },
      to: { ...base, tab: "import" },
    },
    {
      name: "nameTyped sets the table name",
      from: base,
      action: { kind: "nameTyped", name: "t" },
      to: { ...base, name: "t" },
    },
    {
      name: "columnAdded appends a blank column",
      from: base,
      action: { kind: "columnAdded" },
      to: { ...base, columns: [blankColumn(), blankColumn()] },
    },
    {
      name: "columnRemoved drops the column at the index",
      from: twoColumns,
      action: { kind: "columnRemoved", index: 0 },
      to: { ...base, columns: [blankColumn()] },
    },
    {
      name: "columnEdited touches only the target column",
      from: twoColumns,
      action: {
        kind: "columnEdited",
        index: 1,
        patch: { name: "id", type: "INT" },
      },
      to: {
        ...base,
        columns: [blankColumn(), { ...blankColumn(), name: "id", type: "INT" }],
      },
    },
    {
      name: "cellTyped pads a short row up to the column count",
      from: {
        ...base,
        columns: [blankColumn(), blankColumn(), blankColumn()],
        rows: [["a"]],
      },
      action: { kind: "cellTyped", row: 0, column: 2, text: "z" },
      to: {
        ...base,
        columns: [blankColumn(), blankColumn(), blankColumn()],
        rows: [["a", "", "z"]],
      },
    },
    {
      name: "rowAdded adds one empty cell per column",
      from: twoColumns,
      action: { kind: "rowAdded" },
      to: { ...twoColumns, rows: [["", ""]] },
    },
    {
      name: "rowRemoved drops the row at the index",
      from: { ...base, rows: [["x"], ["y"]] },
      action: { kind: "rowRemoved", index: 0 },
      to: { ...base, rows: [["y"]] },
    },
    {
      name: "xmlTyped patches a picked xml spec",
      from: withXml,
      action: {
        kind: "xmlTyped",
        elements: { rootElement: "rows", recordElement: "row" },
      },
      to: {
        ...base,
        picked: {
          kind: "spec",
          spec: { ...xmlSpec, rootElement: "rows", recordElement: "row" },
        },
      },
    },
    {
      name: "pickClicked clears the error and keeps the file already picked",
      from: { ...base, picked: { kind: "spec", spec: csvSpec }, error: "old" },
      action: { kind: "pickClicked" },
      to: {
        ...base,
        picked: { kind: "spec", spec: csvSpec },
        error: null,
        asked: 1,
      },
    },
    {
      name: "a picked csv file records its spec and name and derives the columns",
      from: { ...base, asked: 1 },
      action: {
        kind: "picked",
        asked: 1,
        file: {
          "@type": "csv",
          path: "/t.csv",
          name: "people",
          guess: {
            separator: ",",
            headerLine: 1,
            columns: ["age", "city"],
            sampleRows: [
              ["30", "sp"],
              ["41", "rj"],
            ],
          },
        },
      },
      to: {
        ...base,
        asked: 1,
        name: "people",
        picked: { kind: "spec", spec: { ...csvSpec, name: "people" } },
        columns: [col("age", "INT"), col("city", "STRING")],
        sampleRows: [
          ["30", "sp"],
          ["41", "rj"],
        ],
      },
    },
    {
      name: "an answer to an older pick is ignored",
      from: { ...base, asked: 2 },
      action: { kind: "pickFailed", asked: 1, message: "no" },
      to: { ...base, asked: 2 },
    },
    {
      name: "pickFailed keeps the file already picked and surfaces the message",
      from: { ...base, picked: { kind: "spec", spec: csvSpec }, asked: 1 },
      action: { kind: "pickFailed", asked: 1, message: "no" },
      to: {
        ...base,
        picked: { kind: "spec", spec: csvSpec },
        asked: 1,
        error: "no",
      },
    },
    {
      name: "csvPreviewed refreshes columns, takes the guessed options and lowers the spinner",
      from: {
        ...base,
        previewing: true,
        picked: { kind: "spec", spec: csvSpec },
      },
      action: {
        kind: "csvPreviewed",
        guess: {
          separator: ";",
          headerLine: 2,
          columns: ["n"],
          sampleRows: [["1"]],
        },
      },
      to: {
        ...base,
        previewing: false,
        picked: {
          kind: "spec",
          spec: { ...csvSpec, separator: ";", headerLine: 2 },
        },
        columns: [col("n", "INT")],
        sampleRows: [["1"]],
      },
    },
    {
      name: "previewFailed lowers the spinner and surfaces the message",
      from: { ...base, previewing: true },
      action: { kind: "previewFailed", message: "bad" },
      to: { ...base, previewing: false, error: "bad" },
    },
  ];
  it.each(scenarios)("$name", ({ from, action, to }) => {
    expect(step(from, action).state).toEqual(to);
  });

  it("new csv options ask for a fresh preview of the picked file", () => {
    const { state, effects } = step(withCsv, {
      kind: "csvOptionsChosen",
      separator: ";",
    });
    expect(state.previewing).toBe(true);
    expect(effects).toEqual([
      { kind: "previewCsv", path: "/t.csv", separator: ";", headerLine: 1 },
    ]);
  });

  it("leaving an xml field asks for a fresh preview", () => {
    const { effects } = step(withXml, {
      kind: "xmlLeft",
      elements: { recordElement: "row" },
    });
    expect(effects).toEqual([
      {
        kind: "previewXml",
        path: "/t.xml",
        rootElement: "",
        recordElement: "row",
      },
    ]);
  });

  it("a hand-typed table becomes a memory spec with typed rows", () => {
    const typed: State = {
      ...base,
      name: " t ",
      columns: [{ ...blankColumn(), name: "id", type: "INT" }, blankColumn()],
      rows: [["7", "ignored"]],
    };
    expect(specOf(typed)).toEqual({
      "@type": "memory",
      name: "t",
      columns: [{ ...blankColumn(), name: "id", type: "INT" }],
      rows: [{ id: { int: 7 } }],
    });
  });

  const noops: {
    name: string;
    from: State;
    action: Event;
  }[] = [
    {
      name: "csvOptionsChosen with no csv spec picked",
      from: base,
      action: { kind: "csvOptionsChosen", separator: ";", headerLine: 2 },
    },
    {
      name: "xmlTyped with no xml spec picked",
      from: base,
      action: {
        kind: "xmlTyped",
        elements: { rootElement: "rows", recordElement: "row" },
      },
    },
    {
      name: "csvOptionsChosen against a picked xml spec",
      from: withXml,
      action: { kind: "csvOptionsChosen", separator: ";", headerLine: 2 },
    },
  ];
  it.each(noops)("$name returns the same state", ({ from, action }) => {
    expect(step(from, action)).toEqual({ state: from, effects: [] });
  });
});
