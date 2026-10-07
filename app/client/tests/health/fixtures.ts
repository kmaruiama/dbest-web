export type Literal =
  | {
      int: number;
    }
  | {
      str: string;
    };

export type Column = {
  name: string;
  type: string;
  primaryKey: boolean;
  nullable: boolean;
};

export type TableSpec = {
  "@type": "memory";
  name: string;
  columns: Column[];
  rows: Record<string, Literal>[];
};

export const int = (value: number): Literal => ({ int: value });

export const str = (value: string): Literal => ({ str: value });

const column = (name: string, type: string, primaryKey = false): Column => ({
  name,
  type,
  primaryKey,
  nullable: false,
});

const EMP_COLUMNS = [
  column("id", "INT"),
  column("name", "STRING"),
  column("dept", "INT"),
  column("tags", "STRING"),
];

const EMP_ROWS = [
  { id: int(1), name: str("ana"), dept: int(10), tags: str("x;y") },
  { id: int(2), name: str("bob"), dept: int(20), tags: str("z") },
  { id: int(3), name: str("cid"), dept: int(10), tags: str("w") },
  { id: int(4), name: str("dan"), dept: int(40), tags: str("q") },
];

const DEP_COLUMNS = [column("id", "INT"), column("city", "STRING")];

const DEP_ROWS = [
  { id: int(10), city: str("sp") },
  { id: int(20), city: str("rj") },
  { id: int(30), city: str("bh") },
];

export type FixtureName = "emp" | "dep" | "empKey" | "depKey";

export const FIXTURES: Record<FixtureName, TableSpec> = {
  emp: {
    "@type": "memory",
    name: "emp",
    columns: EMP_COLUMNS,
    rows: EMP_ROWS,
  },
  dep: {
    "@type": "memory",
    name: "dep",
    columns: DEP_COLUMNS,
    rows: DEP_ROWS,
  },
  empKey: {
    "@type": "memory",
    name: "empkey",
    columns: [
      column("id", "INT", true),
      column("name", "STRING"),
      column("dept", "INT"),
      column("tags", "STRING"),
    ],
    rows: EMP_ROWS,
  },
  depKey: {
    "@type": "memory",
    name: "depkey",
    columns: [column("id", "INT", true), column("city", "STRING")],
    rows: DEP_ROWS,
  },
};

export const TABLE_IDS: Record<FixtureName, number> = {
  emp: 1,
  dep: 2,
  empKey: 3,
  depKey: 4,
};

export const TABLE_NAMES: Record<FixtureName, string> = {
  emp: "emp",
  dep: "dep",
  empKey: "empkey",
  depKey: "depkey",
};
