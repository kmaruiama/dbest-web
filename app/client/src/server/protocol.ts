import type { FormDraft, FormValue } from "../form/FormDraft";
import type {
  Ack,
  Bootstrap,
  Caption,
  Catalog,
  Chip,
  ColumnReference,
  Command,
  Condition,
  ConfigInfo,
  CsvPreview,
  Edge,
  FieldSpec,
  FileEntry,
  Literal,
  OperatorType,
  PickedFile,
  PlanNode,
  Position,
  Problem,
  RowsPage,
  SchemaColumn,
  Session,
  SessionMeta,
  SessionView,
  TableColumn,
  TableSpec,
  XmlPreview,
} from "./types";

type WireObject = Record<string, unknown>;

const ports = ["ONLY", "LEFT", "RIGHT"] as const;
const arities = ["source", "unary", "binary"] as const;

const widgets = [
  "text",
  "int",
  "flag",
  "column",
  "qualified",
  "pick",
  "condition",
  "list",
  "rows",
] as const;

const columnTypes = [
  "INT",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "STRING",
  "BOOLEAN",
] as const;

const fail = (path: string, expected: string): never => {
  throw new Error(`Invalid server response at ${path}: expected ${expected}`);
};

const object = (value: unknown, path: string): WireObject =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as WireObject)
    : fail(path, "an object");

const string = (value: unknown, path: string): string =>
  typeof value === "string" ? value : fail(path, "a string");

export const asNumber = (value: unknown, path = "value"): number =>
  typeof value === "number" && Number.isFinite(value)
    ? value
    : fail(path, "a number");

const boolean = (value: unknown, path: string): boolean =>
  typeof value === "boolean" ? value : fail(path, "a boolean");

export const asArray = <T>(
  value: unknown,
  decode: (entry: unknown, path: string) => T,
  path = "value",
): T[] =>
  Array.isArray(value)
    ? value.map((entry, index) => decode(entry, `${path}[${index}]`))
    : fail(path, "an array");

const oneOf = <T extends string>(
  value: unknown,
  options: readonly T[],
  path: string,
): T => {
  const found = string(value, path);
  return options.includes(found as T)
    ? (found as T)
    : fail(path, options.join(" | "));
};

const optional = <T>(
  value: unknown,
  decode: (value: unknown, path: string) => T,
  path: string,
): T | undefined =>
  value === undefined || value === null ? undefined : decode(value, path);

function formValue(value: unknown, path: string): FormValue {
  if (
    value === null ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  )
    return value;
  if (Array.isArray(value))
    return value.map((entry, index) => formValue(entry, `${path}[${index}]`));
  const data = object(value, path);
  if (
    data["@type"] === "cmp" ||
    data["@type"] === "isNull" ||
    data["@type"] === "isNotNull" ||
    data["@type"] === "and" ||
    data["@type"] === "or"
  )
    return condition(data, path);
  const result: FormDraft = {};
  for (const [key, entry] of Object.entries(data))
    result[key] = formValue(entry, `${path}.${key}`);
  return result as {
    [key: string]: FormValue;
  };
}

function draft(
  value: WireObject,
  omit: readonly string[],
  path: string,
): FormDraft {
  const result: FormDraft = {};
  for (const [key, entry] of Object.entries(value))
    if (!omit.includes(key)) result[key] = formValue(entry, `${path}.${key}`);
  return result;
}

function tagged(
  value: unknown,
  path: string,
): {
  tag: string;
  object: WireObject;
} {
  const result = object(value, path);
  return { tag: string(result["@type"], `${path}.@type`), object: result };
}

function node(value: unknown, path: string): PlanNode {
  const decoded = tagged(value, path);
  return {
    kind: decoded.tag,
    fields: draft(decoded.object, ["@type"], path),
  };
}

function columnReference(value: unknown, path: string): ColumnReference {
  const data = object(value, path);
  const source = optional(data.source, string, `${path}.source`);
  return {
    name: string(data.name ?? data.column, `${path}.name`),
    ...(source === undefined ? {} : { source }),
  };
}

function literal(value: unknown, path: string): Literal {
  const data = object(value, path);
  const keys = Object.keys(data);
  if (keys.length !== 1) return fail(path, "one literal value");
  const key = keys[0];
  const item = data[key];
  if (key === "str" || key === "ref")
    return { [key]: string(item, path) } as Literal;
  if (["int", "long", "float", "double"].includes(key))
    return { [key]: asNumber(item, path) } as Literal;
  if (key === "bool") return { bool: boolean(item, path) };
  return fail(path, "a known literal");
}

function condition(value: unknown, path: string): Condition {
  const { tag, object: data } = tagged(value, path);
  if (tag === "cmp")
    return {
      kind: "comparison",
      left: columnReference(data.left, `${path}.left`),
      operator: string(data.op, `${path}.op`),
      value: literal(data.right, `${path}.right`),
    };
  if (tag === "isNull" || tag === "isNotNull")
    return {
      kind: "nullCheck",
      column: columnReference(data.column, `${path}.column`),
      negated: tag === "isNotNull",
    };
  if (tag === "and" || tag === "or")
    return {
      kind: "group",
      operator: tag === "and" ? "all" : "any",
      children: asArray(data.conditions, condition, `${path}.conditions`),
    };
  return fail(`${path}.@type`, "a condition tag");
}

function tableColumn(value: unknown, path: string): TableColumn {
  const data = object(value, path);
  return {
    name: string(data.name, `${path}.name`),
    type: oneOf(data.type, columnTypes, `${path}.type`),
    primaryKey: boolean(data.primaryKey, `${path}.primaryKey`),
    nullable: boolean(data.nullable, `${path}.nullable`),
  };
}

function table(value: unknown, path: string): TableSpec {
  const { tag, object: data } = tagged(value, path);
  const name = string(data.name, `${path}.name`);
  if (tag === "btree")
    return { kind: "btree", name, path: string(data.path, `${path}.path`) };
  const columns = asArray(data.columns, tableColumn, `${path}.columns`);
  if (tag === "csv")
    return {
      kind: "csv",
      name,
      path: string(data.path, `${path}.path`),
      separator: string(data.separator, `${path}.separator`),
      headerLine: asNumber(data.headerLine, `${path}.headerLine`),
      columns,
    };
  if (tag === "xml")
    return {
      kind: "xml",
      name,
      path: string(data.path, `${path}.path`),
      rootElement: string(data.rootElement, `${path}.rootElement`),
      recordElement: string(data.recordElement, `${path}.recordElement`),
      columns,
    };
  if (tag === "memory")
    return {
      kind: "memory",
      name,
      columns,
      rows: asArray(
        data.rows,
        (row, rowPath) => draft(object(row, rowPath), [], rowPath),
        `${path}.rows`,
      ),
    };
  return fail(`${path}.@type`, "a table source");
}

function position(value: unknown, path: string): Position {
  const data = object(value, path);
  return {
    x: asNumber(data.x, `${path}.x`),
    y: asNumber(data.y, `${path}.y`),
  };
}

function edge(value: unknown, path: string): Edge {
  const data = object(value, path);
  return {
    from: asNumber(data.from, `${path}.from`),
    to: asNumber(data.to, `${path}.to`),
    port: oneOf(data.port, ports, `${path}.port`),
  };
}

function map<T>(
  value: unknown,
  decode: (entry: unknown, path: string) => T,
  path: string,
): Map<number, T> {
  if (value === undefined) return new Map();
  const result = new Map<number, T>();
  for (const [key, entry] of Object.entries(object(value, path))) {
    const id = Number(key);
    if (!Number.isSafeInteger(id)) fail(`${path}.${key}`, "a numeric key");
    result.set(id, decode(entry, `${path}.${key}`));
  }
  return result;
}

function session(value: unknown, path: string): Session {
  const data = object(value, path);
  return {
    tables: map(data.tables, table, `${path}.tables`),
    nodes: map(data.nodes, node, `${path}.nodes`),
    edges:
      data.edges === undefined
        ? []
        : asArray(data.edges, edge, `${path}.edges`),
    layout: map(data.layout, position, `${path}.layout`),
  };
}

function caption(value: unknown, path: string): Caption {
  const data = object(value, path);
  const engineClass = optional(data.engineClass, string, `${path}.engineClass`);
  return {
    expression: string(data.expression, `${path}.expression`),
    ...(engineClass === undefined ? {} : { engineClass }),
  };
}

export function sessionView(value: unknown): SessionView {
  const data = object(value, "session view");
  return {
    revision: asNumber(data.revision, "revision"),
    depth: asNumber(data.depth, "depth"),
    session: session(data.session, "session"),
    captions: map(data.captions, caption, "captions"),
    canUndo: boolean(data.canUndo, "canUndo"),
    canRedo: boolean(data.canRedo, "canRedo"),
  };
}

export function ack(value: unknown): Ack {
  const data = object(value, "ack");
  return {
    revision: asNumber(data.revision, "revision"),
    depth: asNumber(data.depth, "depth"),
    canUndo: boolean(data.canUndo, "canUndo"),
    canRedo: boolean(data.canRedo, "canRedo"),
  };
}

export function problem(value: unknown): Problem {
  const data = object(value, "problem");
  return {
    node: asNumber(data.node, "node"),
    message: string(data.message, "message"),
  };
}

export function schemaColumn(value: unknown): SchemaColumn {
  const data = object(value, "schema column");
  return {
    source: string(data.source, "source"),
    name: string(data.name, "name"),
    type: string(data.type, "type"),
    primaryKey: boolean(data.primaryKey, "primaryKey"),
  };
}

export function rowsPage(value: unknown): RowsPage {
  const data = object(value, "rows page");
  return {
    rows: asArray(
      data.rows,
      (row, path) => asArray(row, formValue, path),
      "rows",
    ),
    elapsedMs: asNumber(data.elapsedMs, "elapsedMs"),
  };
}

export function sessionMeta(value: unknown): SessionMeta {
  const data = object(value, "session meta");
  const file = optional(data.file, string, "file");
  return {
    sid: string(data.sid, "sid"),
    name: string(data.name, "name"),
    dirty: boolean(data.dirty, "dirty"),
    ...(file === undefined ? {} : { file }),
  };
}

export function config(value: unknown): ConfigInfo {
  const sessionsDir = optional(
    object(value, "config").sessionsDir,
    string,
    "sessionsDir",
  );
  return sessionsDir === undefined ? {} : { sessionsDir };
}

export function bootstrap(value: unknown): Bootstrap {
  return { token: string(object(value, "bootstrap").token, "token") };
}

export function fileEntry(value: unknown): FileEntry {
  const data = object(value, "file entry");
  return { name: string(data.name, "name"), path: string(data.path, "path") };
}

const previewRows = (value: unknown, path: string) =>
  asArray(
    value,
    (row, rowPath) =>
      asArray(
        row,
        (cell, cellPath) => optional(cell, string, cellPath),
        rowPath,
      ),
    path,
  );

export function csvPreview(value: unknown): CsvPreview {
  const data = object(value, "csv preview");
  return {
    separator: string(data.separator, "separator"),
    headerLine: asNumber(data.headerLine, "headerLine"),
    columns: asArray(data.columns, string, "columns"),
    sampleRows: previewRows(data.sampleRows, "sampleRows"),
  };
}

export function xmlPreview(value: unknown): XmlPreview {
  const data = object(value, "xml preview");
  return {
    ...csvPreview(data),
    rootElement: string(data.rootElement, "rootElement"),
    recordElement: string(data.recordElement, "recordElement"),
    totalRecords: asNumber(data.totalRecords, "totalRecords"),
  };
}

export function pickedFile(value: unknown): PickedFile {
  const data = object(value, "picked file");
  const kind = oneOf(
    data.kind,
    ["cancelled", "head", "dat", "csv", "xml"] as const,
    "kind",
  );
  if (kind === "cancelled") return { kind };
  const path = string(data.path, "path");
  const name = string(data.name, "name");
  if (kind === "csv") return { kind, path, name, ...csvPreview(data) };
  if (kind === "xml") return { kind, path, name, ...xmlPreview(data) };
  return { kind, path, name };
}

function fieldSpec(value: unknown, path: string): FieldSpec {
  const data = object(value, path);
  const options =
    data.options === undefined
      ? undefined
      : asArray(data.options, string, `${path}.options`);
  const item =
    data.item === undefined
      ? undefined
      : oneOf(data.item, widgets, `${path}.item`);
  const of =
    data.of === undefined
      ? undefined
      : asArray(data.of, fieldSpec, `${path}.of`);
  const nullable =
    data.nullable === undefined
      ? undefined
      : boolean(data.nullable, `${path}.nullable`);
  return {
    at: string(data.at, `${path}.at`),
    widget: oneOf(data.widget, widgets, `${path}.widget`),
    ...(options === undefined ? {} : { options }),
    ...(item === undefined ? {} : { item }),
    ...(of === undefined ? {} : { of }),
    ...(nullable === undefined ? {} : { nullable }),
  };
}

function operatorType(value: unknown, path: string): OperatorType {
  const data = object(value, path);
  const fields =
    data.fields === undefined
      ? undefined
      : asArray(data.fields, fieldSpec, `${path}.fields`);
  const variants =
    data.variants === undefined
      ? undefined
      : asArray(data.variants, string, `${path}.variants`);
  return {
    arity: oneOf(data.arity, arities, `${path}.arity`),
    editable: boolean(data.editable, `${path}.editable`),
    ...(fields === undefined ? {} : { fields }),
    ...(variants === undefined ? {} : { variants }),
  };
}

function chip(value: unknown, path: string): Chip {
  const data = object(value, path);
  return {
    key: string(data.key, `${path}.key`),
    type: string(data.type, `${path}.type`),
    symbol: string(data.symbol, `${path}.symbol`),
    category: string(data.category, `${path}.category`),
    template: node(data.template, `${path}.template`),
  };
}

export function catalog(value: unknown): Catalog {
  const data = object(value, "catalog");
  const types: Record<string, OperatorType> = {};
  for (const [key, entry] of Object.entries(object(data.types, "types")))
    types[key] = operatorType(entry, `types.${key}`);
  return {
    categories: asArray(data.categories, string, "categories"),
    types,
    operators: asArray(data.operators, chip, "operators"),
  };
}

const encodeColumn = (column: ColumnReference) => ({
  source: column.source ?? null,
  name: column.name,
});

const encodeCondition = (item: Condition): FormValue =>
  item.kind === "comparison"
    ? {
        "@type": "cmp",
        left: encodeColumn(item.left),
        op: item.operator,
        right: item.value,
      }
    : item.kind === "nullCheck"
      ? {
          "@type": item.negated ? "isNotNull" : "isNull",
          column: encodeColumn(item.column),
        }
      : {
          "@type": item.operator === "all" ? "and" : "or",
          conditions: item.children.map(encodeCondition),
        };

function encodeValue(value: FormValue): FormValue {
  if (Array.isArray(value)) return value.map(encodeValue);
  if (isCondition(value)) return encodeCondition(value);
  if (value !== null && typeof value === "object")
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, encodeValue(item)]),
    );
  return value;
}

function isCondition(value: FormValue): value is Condition {
  if (value === null || typeof value !== "object" || !("kind" in value))
    return false;
  if (value.kind === "comparison")
    return "left" in value && "operator" in value && "value" in value;
  if (value.kind === "nullCheck")
    return "column" in value && "negated" in value;
  return value.kind === "group" && "operator" in value && "children" in value;
}

const encodeNode = (item: PlanNode) => ({
  "@type": item.kind,
  ...Object.fromEntries(
    Object.entries(item.fields).map(([key, value]) => [
      key,
      encodeValue(value),
    ]),
  ),
});

const encodeTable = (item: TableSpec) =>
  item.kind === "btree"
    ? { "@type": "btree", name: item.name, path: item.path }
    : item.kind === "memory"
      ? {
          "@type": "memory",
          name: item.name,
          columns: item.columns,
          rows: item.rows,
        }
      : item.kind === "csv"
        ? {
            "@type": "csv",
            name: item.name,
            path: item.path,
            separator: item.separator,
            headerLine: item.headerLine,
            columns: item.columns,
          }
        : {
            "@type": "xml",
            name: item.name,
            path: item.path,
            rootElement: item.rootElement,
            recordElement: item.recordElement,
            columns: item.columns,
          };

export function command(item: Command | object): object {
  if (!("kind" in item)) return item;
  switch (item.kind) {
    case "addTable":
      return {
        "@type": "addTable",
        id: item.id,
        spec: encodeTable(item.spec),
      };
    case "removeTable":
      return { "@type": "removeTable", id: item.id };
    case "addNode":
      return {
        "@type": "addNode",
        id: item.id,
        node: encodeNode(item.node),
        at: item.at,
      };
    case "setNode":
      return {
        "@type": "setNode",
        id: item.id,
        node: encodeNode(item.node),
      };
    case "removeNode":
      return { "@type": "removeNode", id: item.id };
    case "connect":
      return { "@type": "connect", edge: item.edge };
    case "disconnect":
      return { "@type": "disconnect", edge: item.edge };
    case "move":
      return { "@type": "move", id: item.id, to: item.to };
    case "batch":
      return { "@type": "batch", commands: item.commands.map(command) };
  }
}

export { condition, encodeCondition };
