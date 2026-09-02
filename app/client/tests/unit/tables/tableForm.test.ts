import { describe, expect, it } from "vitest";
import {
  blankColumn,
  type Column,
  guessColumnType,
  initialForm,
  reduceForm,
  type TableFormAction,
  type TableFormState,
} from "../../../src/tables/tableForm";
import type { ColumnType } from "../../../src/server/literals";
import type { CsvTable, XmlTable } from "../../../src/server/types";

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
    rows: (string | undefined)[][];
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
      rows: [[""], ["  "], [undefined]],
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

describe("initialForm", () => {
  it("starts with one blank column, nothing picked, and no error", () => {
    expect(initialForm("import")).toEqual({
      tab: "import",
      name: "",
      columns: [blankColumn()],
      rows: [],
      picked: { kind: "none" },
      sampleRows: [],
      previewing: false,
      error: null,
    });
  });
});

describe("reduceForm", () => {
  const base = initialForm("create");
  const twoColumns: TableFormState = {
    ...base,
    columns: [blankColumn(), blankColumn()],
  };
  const csvSpec: CsvTable = {
    kind: "csv",
    name: "t",
    path: "/t.csv",
    separator: ",",
    headerLine: 1,
    columns: [],
  };
  const xmlSpec: XmlTable = {
    kind: "xml",
    name: "t",
    path: "/t.xml",
    rootElement: "",
    recordElement: "",
    columns: [],
  };
  const withCsv: TableFormState = {
    ...base,
    picked: { kind: "spec", spec: csvSpec },
  };
  const withXml: TableFormState = {
    ...base,
    picked: { kind: "spec", spec: xmlSpec },
  };

  const scenarios: {
    name: string;
    from: TableFormState;
    action: TableFormAction;
    to: TableFormState;
  }[] = [
    {
      name: "tab switches the active tab",
      from: base,
      action: { kind: "tab", tab: "import" },
      to: { ...base, tab: "import" },
    },
    {
      name: "name sets the table name",
      from: base,
      action: { kind: "name", name: "t" },
      to: { ...base, name: "t" },
    },
    {
      name: "addColumn appends a blank column",
      from: base,
      action: { kind: "addColumn" },
      to: { ...base, columns: [blankColumn(), blankColumn()] },
    },
    {
      name: "removeColumn drops the column at the index",
      from: twoColumns,
      action: { kind: "removeColumn", index: 0 },
      to: { ...base, columns: [blankColumn()] },
    },
    {
      name: "patchColumn touches only the target column",
      from: twoColumns,
      action: {
        kind: "patchColumn",
        index: 1,
        patch: { name: "id", type: "INT" },
      },
      to: {
        ...base,
        columns: [blankColumn(), { ...blankColumn(), name: "id", type: "INT" }],
      },
    },
    {
      name: "setCell pads a short row up to the column count",
      from: {
        ...base,
        columns: [blankColumn(), blankColumn(), blankColumn()],
        rows: [["a"]],
      },
      action: { kind: "setCell", row: 0, column: 2, text: "z" },
      to: {
        ...base,
        columns: [blankColumn(), blankColumn(), blankColumn()],
        rows: [["a", "", "z"]],
      },
    },
    {
      name: "addRow adds one empty cell per column",
      from: twoColumns,
      action: { kind: "addRow" },
      to: { ...twoColumns, rows: [["", ""]] },
    },
    {
      name: "removeRow drops the row at the index",
      from: { ...base, rows: [["x"], ["y"]] },
      action: { kind: "removeRow", index: 0 },
      to: { ...base, rows: [["y"]] },
    },
    {
      name: "changeCsv patches a picked csv spec",
      from: withCsv,
      action: { kind: "changeCsv", patch: { separator: ";", headerLine: 2 } },
      to: {
        ...base,
        picked: {
          kind: "spec",
          spec: { ...csvSpec, separator: ";", headerLine: 2 },
        },
      },
    },
    {
      name: "changeXml patches a picked xml spec",
      from: withXml,
      action: {
        kind: "changeXml",
        patch: { rootElement: "rows", recordElement: "row" },
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
      name: "pickStart enters picking and clears the error",
      from: { ...base, error: "old" },
      action: { kind: "pickStart" },
      to: { ...base, picked: { kind: "picking" }, error: null },
    },
    {
      name: "pickCancelled returns to nothing picked",
      from: { ...base, picked: { kind: "picking" } },
      action: { kind: "pickCancelled" },
      to: { ...base, picked: { kind: "none" } },
    },
    {
      name: "pickChosen without a preview records the spec and name",
      from: base,
      action: { kind: "pickChosen", name: "people", spec: csvSpec },
      to: { ...base, name: "people", picked: { kind: "spec", spec: csvSpec } },
    },
    {
      name: "pickChosen with a preview derives the columns",
      from: base,
      action: {
        kind: "pickChosen",
        name: "people",
        spec: csvSpec,
        preview: {
          columns: ["age", "city"],
          sampleRows: [
            ["30", "sp"],
            ["41", "rj"],
          ],
        },
      },
      to: {
        ...base,
        name: "people",
        picked: { kind: "spec", spec: csvSpec },
        columns: [col("age", "INT"), col("city", "STRING")],
        sampleRows: [
          ["30", "sp"],
          ["41", "rj"],
        ],
      },
    },
    {
      name: "pickFailed clears the pick and surfaces the message",
      from: { ...base, picked: { kind: "picking" } },
      action: { kind: "pickFailed", message: "no" },
      to: { ...base, picked: { kind: "none" }, error: "no" },
    },
    {
      name: "previewStart raises the spinner and clears the error",
      from: { ...base, error: "old" },
      action: { kind: "previewStart" },
      to: { ...base, previewing: true, error: null },
    },
    {
      name: "previewApplied refreshes columns and lowers the spinner",
      from: {
        ...base,
        previewing: true,
        picked: { kind: "spec", spec: csvSpec },
      },
      action: {
        kind: "previewApplied",
        spec: csvSpec,
        preview: { columns: ["n"], sampleRows: [["1"]] },
      },
      to: {
        ...base,
        previewing: false,
        picked: { kind: "spec", spec: csvSpec },
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
    expect(reduceForm(from, action)).toEqual(to);
  });

  const noops: {
    name: string;
    from: TableFormState;
    action: TableFormAction;
  }[] = [
    {
      name: "changeCsv with no csv spec picked",
      from: base,
      action: { kind: "changeCsv", patch: { separator: ";", headerLine: 2 } },
    },
    {
      name: "changeXml with no xml spec picked",
      from: base,
      action: {
        kind: "changeXml",
        patch: { rootElement: "rows", recordElement: "row" },
      },
    },
    {
      name: "changeCsv against a picked xml spec",
      from: withXml,
      action: { kind: "changeCsv", patch: { separator: ";", headerLine: 2 } },
    },
  ];
  it.each(noops)("$name returns the same state", ({ from, action }) => {
    expect(reduceForm(from, action)).toBe(from);
  });
});
