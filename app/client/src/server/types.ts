import type { FormDraft, FormValue } from "../form/FormDraft";

export type NodeId = number;

export type TableId = number;

export type PlanNode = {
  kind: string;
  fields: FormDraft;
};

export type Literal =
  | {
      int: number;
    }
  | {
      long: number;
    }
  | {
      float: number;
    }
  | {
      double: number;
    }
  | {
      bool: boolean;
    }
  | {
      str: string;
    }
  | {
      ref: string;
    };

export type ColumnReference = {
  source?: string;
  name: string;
};

export type ComparisonCondition = {
  kind: "comparison";
  left: ColumnReference;
  operator: string;
  value: Literal;
};

export type NullCheckCondition = {
  kind: "nullCheck";
  column: ColumnReference;
  negated: boolean;
};

export type GroupCondition = {
  kind: "group";
  operator: "all" | "any";
  children: Condition[];
};

export type Condition =
  ComparisonCondition | NullCheckCondition | GroupCondition;

export type ColumnType =
  "INT" | "LONG" | "FLOAT" | "DOUBLE" | "STRING" | "BOOLEAN";

export type TableColumn = {
  name: string;
  type: ColumnType;
  primaryKey: boolean;
  nullable: boolean;
};

export type MemoryTable = {
  kind: "memory";
  name: string;
  columns: TableColumn[];
  rows: Record<string, FormValue>[];
};

export type CsvTable = {
  kind: "csv";
  name: string;
  path: string;
  separator: string;
  headerLine: number;
  columns: TableColumn[];
};

export type XmlTable = {
  kind: "xml";
  name: string;
  path: string;
  rootElement: string;
  recordElement: string;
  columns: TableColumn[];
};

export type BTreeTable = {
  kind: "btree";
  name: string;
  path: string;
};

export type TableSpec = MemoryTable | CsvTable | XmlTable | BTreeTable;

export type Port = "ONLY" | "LEFT" | "RIGHT";

export type Position = {
  x: number;
  y: number;
};

export type Edge = {
  from: NodeId;
  to: NodeId;
  port: Port;
};

export type Problem = {
  node: NodeId;
  message: string;
};

export type Session = {
  tables: Map<TableId, TableSpec>;
  nodes: Map<NodeId, PlanNode>;
  edges: Edge[];
  layout: Map<NodeId, Position>;
};

export type Caption = {
  engineClass?: string;
  expression: string;
};

export type SessionView = {
  revision: number;
  depth: number;
  session: Session;
  captions: Map<NodeId, Caption>;
  canUndo: boolean;
  canRedo: boolean;
};

export type SessionMeta = {
  sid: string;
  name: string;
  dirty: boolean;
  file?: string;
};

export type ConfigInfo = {
  sessionsDir?: string;
};

export type FileEntry = {
  name: string;
  path: string;
};

export type SchemaColumn = {
  source: string;
  name: string;
  type: string;
  primaryKey: boolean;
};

export type CsvPreview = {
  separator: string;
  headerLine: number;
  columns: string[];
  sampleRows: (string | undefined)[][];
};

export type XmlPreview = {
  rootElement: string;
  recordElement: string;
  columns: string[];
  sampleRows: (string | undefined)[][];
  totalRecords: number;
};

export type PickedFile =
  | {
      kind: "cancelled";
    }
  | {
      kind: "head" | "dat";
      path: string;
      name: string;
    }
  | ({
      kind: "csv";
      path: string;
      name: string;
    } & CsvPreview)
  | ({
      kind: "xml";
      path: string;
      name: string;
    } & XmlPreview);

export type RowsPage = {
  rows: FormValue[][];
  elapsedMs: number;
};

export type Bootstrap = {
  token: string;
};

export type Arity = "source" | "unary" | "binary";

export type Widget =
  | "text"
  | "int"
  | "flag"
  | "column"
  | "qualified"
  | "pick"
  | "condition"
  | "list"
  | "rows";

export type FieldSpec = {
  at: string;
  widget: Widget;
  options?: string[];
  item?: Widget;
  of?: FieldSpec[];
  nullable?: boolean;
};

export type OperatorType = {
  arity: Arity;
  editable: boolean;
  fields?: FieldSpec[];
  variants?: string[];
};

export type Chip = {
  key: string;
  type: string;
  symbol: string;
  category: string;
  template: PlanNode;
};

export type Catalog = {
  categories: string[];
  types: Record<string, OperatorType>;
  operators: Chip[];
};

export type Command =
  | {
      kind: "addTable";
      id: TableId;
      spec: TableSpec;
    }
  | {
      kind: "removeTable";
      id: TableId;
    }
  | {
      kind: "addNode";
      id: NodeId;
      node: PlanNode;
      at: Position;
    }
  | {
      kind: "setNode";
      id: NodeId;
      node: PlanNode;
    }
  | {
      kind: "removeNode";
      id: NodeId;
    }
  | {
      kind: "connect";
      edge: Edge;
    }
  | {
      kind: "disconnect";
      edge: Edge;
    }
  | {
      kind: "move";
      id: NodeId;
      to: Position;
    }
  | {
      kind: "batch";
      commands: Command[];
    };

export type Ack = {
  revision: number;
  depth: number;
  canUndo: boolean;
  canRedo: boolean;
};
