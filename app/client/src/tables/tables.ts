import { columnLiteral, columnTypeOf } from "../server/literals";
import type {
  BTreeTable,
  Column,
  ColumnType,
  CsvGuess,
  CsvTable,
  Picked as PickedFile,
  TableSpec,
  XmlGuess,
  XmlTable,
} from "../server/types";
import type { Stepped } from "../ui/useStep";

export type TableMode = "create" | "import";

export type Picked =
  { kind: "none" } | { kind: "spec"; spec: CsvTable | XmlTable | BTreeTable };

export type State = {
  tab: TableMode;
  name: string;
  columns: Column[];
  rows: string[][];
  picked: Picked;
  sampleRows: (string | null)[][];
  previewing: boolean;
  error: string | null;
  asked: number;
};

type XmlElements = Pick<XmlTable, "rootElement" | "recordElement">;

export type Event =
  | { kind: "tabClicked"; tab: TableMode }
  | { kind: "nameTyped"; name: string }
  | { kind: "columnEdited"; index: number; patch: Partial<Column> }
  | { kind: "columnAdded" }
  | { kind: "columnRemoved"; index: number }
  | { kind: "cellTyped"; row: number; column: number; text: string }
  | { kind: "rowAdded" }
  | { kind: "rowRemoved"; index: number }
  | { kind: "pickClicked" }
  | { kind: "picked"; asked: number; file: PickedFile }
  | { kind: "pickFailed"; asked: number; message: string }
  | {
      kind: "csvOptionsChosen";
      separator?: string;
      headerLine?: number;
    }
  | { kind: "xmlTyped"; elements: XmlElements }
  | { kind: "xmlLeft"; elements: Partial<XmlElements> }
  | { kind: "csvPreviewed"; guess: CsvGuess }
  | { kind: "xmlPreviewed"; guess: XmlGuess }
  | { kind: "previewFailed"; message: string };

export type Effect =
  | { kind: "pick"; asked: number }
  | { kind: "previewCsv"; path: string; headerLine: number; separator: string }
  | {
      kind: "previewXml";
      path: string;
      rootElement?: string;
      recordElement?: string;
    };

export function initial(mode: TableMode): Stepped<State, Effect> {
  return {
    state: {
      tab: mode,
      name: "",
      columns: [blankColumn()],
      rows: [],
      picked: { kind: "none" },
      sampleRows: [],
      previewing: false,
      error: null,
      asked: 0,
    },
    effects: [],
  };
}

export function step(state: State, event: Event): Stepped<State, Effect> {
  switch (event.kind) {
    case "tabClicked":
      return only({ ...state, tab: event.tab });
    case "nameTyped":
      return only({ ...state, name: event.name });
    case "columnEdited":
      return only({
        ...state,
        columns: state.columns.map((column, at) =>
          at === event.index ? { ...column, ...event.patch } : column,
        ),
      });
    case "columnAdded":
      return only({ ...state, columns: [...state.columns, blankColumn()] });
    case "columnRemoved":
      return only({
        ...state,
        columns: state.columns.filter((_column, at) => at !== event.index),
      });
    case "cellTyped":
      return only({
        ...state,
        rows: state.rows.map((row, at) =>
          at === event.row
            ? typedCell(row, state.columns.length, event.column, event.text)
            : row,
        ),
      });
    case "rowAdded":
      return only({
        ...state,
        rows: [...state.rows, state.columns.map(() => "")],
      });
    case "rowRemoved":
      return only({
        ...state,
        rows: state.rows.filter((_row, at) => at !== event.index),
      });
    case "pickClicked": {
      const asked = state.asked + 1;
      return {
        state: { ...state, asked, error: null },
        effects: [{ kind: "pick", asked }],
      };
    }
    case "picked":
      return only(
        event.asked === state.asked ? withFile(state, event.file) : state,
      );
    case "pickFailed":
      return only(
        event.asked === state.asked
          ? { ...state, error: event.message }
          : state,
      );
    case "csvOptionsChosen": {
      const spec = pickedCsv(state);
      if (spec === null) return only(state);
      return {
        state: { ...state, previewing: true, error: null },
        effects: [
          {
            kind: "previewCsv",
            path: spec.path,
            separator: event.separator ?? spec.separator,
            headerLine: event.headerLine ?? spec.headerLine,
          },
        ],
      };
    }
    case "xmlTyped": {
      const spec = pickedXml(state);
      if (spec === null) return only(state);
      return only(withSpec(state, { ...spec, ...event.elements }));
    }
    case "xmlLeft": {
      const spec = pickedXml(state);
      if (spec === null) return only(state);
      return {
        state: { ...state, previewing: true, error: null },
        effects: [
          {
            kind: "previewXml",
            path: spec.path,
            rootElement:
              event.elements.rootElement ?? spec.rootElement ?? undefined,
            recordElement:
              event.elements.recordElement ?? spec.recordElement ?? undefined,
          },
        ],
      };
    }
    case "csvPreviewed": {
      const spec = pickedCsv(state);
      const next = previewed(state, event.guess);
      return only(
        spec !== null
          ? withSpec(next, {
              ...spec,
              separator: event.guess.separator,
              headerLine: event.guess.headerLine,
            })
          : next,
      );
    }
    case "xmlPreviewed": {
      const spec = pickedXml(state);
      const next = previewed(state, event.guess);
      return only(
        spec !== null
          ? withSpec(next, {
              ...spec,
              rootElement: event.guess.rootElement,
              recordElement: event.guess.recordElement,
            })
          : next,
      );
    }
    case "previewFailed":
      return only({ ...state, previewing: false, error: event.message });
  }
}

function typedCell(
  row: string[],
  width: number,
  column: number,
  text: string,
): string[] {
  const next = [...row];
  while (next.length < width) next.push("");
  next[column] = text;
  return next;
}

function pickedCsv(state: State): CsvTable | null {
  const spec = pickedSpec(state);
  return spec !== null && spec["@type"] === "csv" ? spec : null;
}

function pickedXml(state: State): XmlTable | null {
  const spec = pickedSpec(state);
  return spec !== null && spec["@type"] === "xml" ? spec : null;
}

export function specOf(state: State): TableSpec | null {
  const columns = state.columns
    .filter((column) => column.name.trim().length > 0)
    .map((column) => ({ ...column, name: column.name.trim() }));
  const name = state.name.trim();
  if (state.tab === "create") {
    return {
      "@type": "memory",
      name,
      columns,
      rows: state.rows.map((row) => memoryRecord(columns, row)),
    };
  }
  const spec = pickedSpec(state);
  if (spec === null) return null;
  return spec["@type"] === "btree"
    ? { ...spec, name }
    : { ...spec, name, columns };
}

function memoryRecord(
  columns: Column[],
  row: string[],
): Record<string, ReturnType<typeof columnLiteral>> {
  const record: Record<string, ReturnType<typeof columnLiteral>> = {};
  columns.forEach((column, index) => {
    record[column.name] = columnLiteral(column.type, row[index] ?? "");
  });
  return record;
}

export function pickedSpec(
  state: State,
): CsvTable | XmlTable | BTreeTable | null {
  return state.picked.kind === "spec" ? state.picked.spec : null;
}

export function guessColumnType(
  index: number,
  sampleRows: (string | null)[][],
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

function only(state: State): Stepped<State, Effect> {
  return { state, effects: [] };
}

function withSpec(state: State, spec: CsvTable | XmlTable | BTreeTable): State {
  return { ...state, picked: { kind: "spec", spec } };
}

function withFile(state: State, file: PickedFile): State {
  switch (file["@type"]) {
    case "cancelled":
      return state;
    case "csv":
      return previewed(
        withSpec(
          { ...state, name: file.name },
          {
            "@type": "csv",
            name: file.name,
            path: file.path,
            separator: file.guess.separator,
            headerLine: file.guess.headerLine,
            delimiter: '"',
            columns: [],
          },
        ),
        file.guess,
      );
    case "xml":
      return previewed(
        withSpec(
          { ...state, name: file.name },
          {
            "@type": "xml",
            name: file.name,
            path: file.path,
            rootElement: file.guess.rootElement,
            recordElement: file.guess.recordElement,
            columns: [],
          },
        ),
        file.guess,
      );
    case "btree":
      return withSpec(
        { ...state, name: file.name },
        {
          "@type": "btree",
          name: file.name,
          path: file.path,
          cacheSize: 100_000,
        },
      );
  }
}

function previewed(
  state: State,
  guess: { columns: string[]; sampleRows: (string | null)[][] },
): State {
  return {
    ...state,
    previewing: false,
    columns: guess.columns.map((name, index) => ({
      ...blankColumn(),
      name,
      type: guessColumnType(index, guess.sampleRows),
    })),
    sampleRows: guess.sampleRows,
  };
}
