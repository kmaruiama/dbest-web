import { columnTypeOf, type ColumnType } from "../server/literals";
import type { CsvTable, TableSpec, XmlTable } from "../server/types";
import type { TableMode } from "./TableModal";

export type Column = {
  name: string;
  type: ColumnType;
  primaryKey: boolean;
  nullable: boolean;
};

export type Picked =
  | {
      kind: "none";
    }
  | {
      kind: "picking";
    }
  | {
      kind: "spec";
      spec:
        | CsvTable
        | XmlTable
        | Extract<
            TableSpec,
            {
              kind: "btree";
            }
          >;
    };

type PickedSpec = Extract<
  Picked,
  {
    kind: "spec";
  }
>["spec"];

export function guessColumnType(
  index: number,
  sampleRows: (string | undefined)[][],
): ColumnType {
  const votes = new Map<ColumnType, number>();
  for (const row of sampleRows) {
    const value = row[index];
    if (value === null || value === undefined || value.trim().length === 0)
      continue;
    const type = columnTypeOf(value);
    votes.set(type, (votes.get(type) ?? 0) + 1);
  }
  let best: ColumnType = "STRING";
  let bestVotes = 0;
  for (const [type, count] of votes) {
    if (count > bestVotes) {
      best = type;
      bestVotes = count;
    }
  }
  return best;
}

export function blankColumn(): Column {
  return { name: "", type: "STRING", primaryKey: false, nullable: true };
}

export type TableFormState = {
  tab: TableMode;
  name: string;
  columns: Column[];
  rows: string[][];
  picked: Picked;
  sampleRows: (string | undefined)[][];
  previewing: boolean;
  error: string | null;
};

export function initialForm(mode: TableMode): TableFormState {
  return {
    tab: mode,
    name: "",
    columns: [blankColumn()],
    rows: [],
    picked: { kind: "none" },
    sampleRows: [],
    previewing: false,
    error: null,
  };
}

type Preview = {
  columns: string[];
  sampleRows: (string | undefined)[][];
};

export type TableFormAction =
  | {
      kind: "tab";
      tab: TableMode;
    }
  | {
      kind: "name";
      name: string;
    }
  | {
      kind: "patchColumn";
      index: number;
      patch: Partial<Column>;
    }
  | {
      kind: "addColumn";
    }
  | {
      kind: "removeColumn";
      index: number;
    }
  | {
      kind: "setCell";
      row: number;
      column: number;
      text: string;
    }
  | {
      kind: "addRow";
    }
  | {
      kind: "removeRow";
      index: number;
    }
  | {
      kind: "changeCsv";
      patch: Pick<CsvTable, "separator" | "headerLine">;
    }
  | {
      kind: "changeXml";
      patch: Pick<XmlTable, "rootElement" | "recordElement">;
    }
  | {
      kind: "pickStart";
    }
  | {
      kind: "pickCancelled";
    }
  | {
      kind: "pickChosen";
      spec: PickedSpec;
      name: string;
      preview?: Preview;
    }
  | {
      kind: "pickFailed";
      message: string;
    }
  | {
      kind: "previewStart";
    }
  | {
      kind: "previewApplied";
      preview: Preview;
      spec: CsvTable | XmlTable;
    }
  | {
      kind: "previewFailed";
      message: string;
    };

function columnsFromPreview(preview: Preview): Column[] {
  return preview.columns.map((name, index) => ({
    name,
    type: guessColumnType(index, preview.sampleRows),
    primaryKey: false,
    nullable: true,
  }));
}

export function reduceForm(
  state: TableFormState,
  action: TableFormAction,
): TableFormState {
  switch (action.kind) {
    case "tab":
      return { ...state, tab: action.tab };
    case "name":
      return { ...state, name: action.name };
    case "patchColumn":
      return {
        ...state,
        columns: state.columns.map((column, at) =>
          at === action.index ? { ...column, ...action.patch } : column,
        ),
      };
    case "addColumn":
      return { ...state, columns: [...state.columns, blankColumn()] };
    case "removeColumn":
      return {
        ...state,
        columns: state.columns.filter((_column, at) => at !== action.index),
      };
    case "setCell":
      return {
        ...state,
        rows: state.rows.map((row, at) => {
          if (at !== action.row) return row;
          const next = [...row];
          while (next.length < state.columns.length) next.push("");
          next[action.column] = action.text;
          return next;
        }),
      };
    case "addRow":
      return {
        ...state,
        rows: [...state.rows, state.columns.map(() => "")],
      };
    case "removeRow":
      return {
        ...state,
        rows: state.rows.filter((_row, at) => at !== action.index),
      };
    case "changeCsv":
      return state.picked.kind === "spec" && state.picked.spec.kind === "csv"
        ? {
            ...state,
            picked: {
              kind: "spec",
              spec: { ...state.picked.spec, ...action.patch },
            },
          }
        : state;
    case "changeXml":
      return state.picked.kind === "spec" && state.picked.spec.kind === "xml"
        ? {
            ...state,
            picked: {
              kind: "spec",
              spec: { ...state.picked.spec, ...action.patch },
            },
          }
        : state;
    case "pickStart":
      return { ...state, picked: { kind: "picking" }, error: null };
    case "pickCancelled":
      return { ...state, picked: { kind: "none" } };
    case "pickChosen":
      return {
        ...state,
        name: action.name,
        picked: { kind: "spec", spec: action.spec },
        ...(action.preview === undefined
          ? {}
          : {
              columns: columnsFromPreview(action.preview),
              sampleRows: action.preview.sampleRows,
            }),
      };
    case "pickFailed":
      return {
        ...state,
        picked: { kind: "none" },
        error: action.message,
      };
    case "previewStart":
      return { ...state, previewing: true, error: null };
    case "previewApplied":
      return {
        ...state,
        previewing: false,
        columns: columnsFromPreview(action.preview),
        sampleRows: action.preview.sampleRows,
        picked:
          state.picked.kind === "spec"
            ? { kind: "spec", spec: action.spec }
            : state.picked,
      };
    case "previewFailed":
      return { ...state, previewing: false, error: action.message };
  }
}
