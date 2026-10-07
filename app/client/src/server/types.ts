export type NodeId = number;

export type TableId = number;

export type FormValue =
  string | number | boolean | null | FormValue[] | { [key: string]: FormValue };

export type Literal =
  | { int: number }
  | { long: number }
  | { float: number }
  | { double: number }
  | { bool: boolean }
  | { str: string }
  | { ref: string };

export type Comparison = {
  "@type": "cmp";
  left: string;
  op: string;
  right: Literal;
};

export type NullCheck = {
  "@type": "isNull" | "isNotNull";
  column: string;
};

export type Group = {
  "@type": "and" | "or";
  conditions: Condition[];
};

export type Condition = Comparison | NullCheck | Group;

export type Node = { "@type": string; [field: string]: FormValue };

export type ColumnType =
  "INT" | "LONG" | "FLOAT" | "DOUBLE" | "STRING" | "BOOLEAN";

export type Column = {
  name: string;
  type: ColumnType;
  primaryKey: boolean;
  nullable: boolean;
};

export type MemoryTable = {
  "@type": "memory";
  name: string;
  columns: Column[];
  rows: Record<string, Literal | null>[];
};

export type CsvTable = {
  "@type": "csv";
  name: string;
  path: string;
  columns: Column[];
  separator: string;
  delimiter: string;
  headerLine: number;
};

export type XmlTable = {
  "@type": "xml";
  name: string;
  path: string;
  columns: Column[];
  rootElement: string | null;
  recordElement: string | null;
};

export type BTreeTable = {
  "@type": "btree";
  name: string;
  path: string;
  cacheSize: number;
};

export type TableSpec = MemoryTable | CsvTable | XmlTable | BTreeTable;

export type Port = "ONLY" | "LEFT" | "RIGHT";

export type Position = { x: number; y: number };

export type Edge = { from: NodeId; to: NodeId; port: Port };

export type Session = {
  tables: Record<TableId, TableSpec>;
  nodes: Record<NodeId, Node>;
  edges: Edge[];
  layout: Record<NodeId, Position>;
};

export type Caption = {
  engineClass: string | null;
  expression: string;
  alias: string | null;
};

export type SessionView = {
  session: Session;
  captions: Record<NodeId, Caption>;
  undoDepth: number;
  redoDepth: number;
};

export type Problem = { node: NodeId; message: string };

export type SessionMeta = { sid: string; name: string; file: string };

export type Config = { sessionsDir: string | null; open: string[] };

export type FileEntry = { name: string; path: string };

export type SchemaColumn = {
  source: string;
  name: string;
  type: ColumnType;
  primaryKey: boolean;
};

export type RowsPage = { rows: FormValue[][]; elapsedMs: number };

export type Metric = { name: string; value: number };

export type Measure = {
  tuples: number;
  exhausted: boolean;
  elapsedMs: number;
  counters: Metric[];
};

export type Columns = {
  inputs: Partial<Record<Port, string[]>>;
  outer: string[];
};

export type CsvGuess = {
  separator: string;
  headerLine: number;
  columns: string[];
  sampleRows: (string | null)[][];
};

export type XmlGuess = {
  rootElement: string;
  recordElement: string;
  columns: string[];
  sampleRows: (string | null)[][];
  totalRecords: number;
};

export type Picked =
  | { "@type": "cancelled" }
  | { "@type": "csv"; path: string; name: string; guess: CsvGuess }
  | { "@type": "xml"; path: string; name: string; guess: XmlGuess }
  | { "@type": "btree"; path: string; name: string };

export type Arity = "source" | "unary" | "binary";

export type Widget =
  "text" | "int" | "flag" | "column" | "pick" | "condition" | "list" | "rows";

export type FieldSpec = {
  at: string;
  widget: Widget;
  options: string[];
  item: Widget | null;
  of: FieldSpec[];
  nullable: boolean;
};

export type OperatorType = {
  arity: Arity;
  editable: boolean;
  fields: FieldSpec[];
  variants: string[];
};

export type Chip = {
  key: string;
  type: string;
  symbol: string;
  category: string;
  template: Node;
};

export type Catalog = {
  categories: string[];
  types: Record<string, OperatorType>;
  operators: Chip[];
  placeholder: string;
};

export type Command =
  | { "@type": "addTable"; id: TableId; spec: TableSpec }
  | { "@type": "removeTable"; id: TableId }
  | { "@type": "addNode"; id: NodeId; node: Node; at: Position }
  | { "@type": "setNode"; id: NodeId; node: Node }
  | { "@type": "removeNode"; id: NodeId }
  | { "@type": "connect"; edge: Edge }
  | { "@type": "disconnect"; edge: Edge }
  | { "@type": "move"; id: NodeId; to: Position }
  | { "@type": "batch"; commands: Command[] };
