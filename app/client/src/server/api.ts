import type { Bootstrap, Command, NodeId } from "./types";
import type { FormValue } from "../form/FormDraft";
import * as protocol from "./protocol";

export class EngineBusyError extends Error {}

let apiToken: string | null = null;

export function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function errorMessage(text: string): string {
  try {
    const body: unknown = JSON.parse(text);
    if (typeof body === "object" && body !== null && !Array.isArray(body)) {
      const error = Object.entries(body).find(([key]) => key === "error")?.[1];
      if (typeof error === "string") return error;
    }
  } catch {
    return text;
  }
  return text;
}

async function request<Value>(
  parse: (json: unknown) => Value,
  path: string,
  init?: RequestInit,
): Promise<Value> {
  const headers = new Headers(init?.headers);
  if (init?.body) headers.set("Content-Type", "application/json");
  if (init?.method !== undefined && init.method !== "GET") {
    if (apiToken === null)
      throw new Error(
        "a sessao segura da aplicacao ainda nao foi inicializada",
      );
    headers.set("X-DBest-Token", apiToken);
  }
  const response = await fetch(path, { ...init, headers });
  const text = await response.text();
  if (!response.ok) {
    const message = errorMessage(text);
    console.error(
      `${init?.method ?? "GET"} ${path} -> ${response.status} ${message}`,
    );
    if (response.status === 409) throw new EngineBusyError(message);
    throw new Error(message);
  }
  return parse(text ? JSON.parse(text) : undefined);
}

function post(body?: unknown): RequestInit {
  return body === undefined
    ? { method: "POST" }
    : { method: "POST", body: JSON.stringify(body) };
}

function nothing(): void {
  return undefined;
}

function ws(sid: string, path: string): string {
  return `/sessions/${sid}${path}`;
}

async function* rowsAllStream(
  sid: string,
  node: NodeId,
  signal?: AbortSignal,
): AsyncGenerator<FormValue[][]> {
  const response = await fetch(ws(sid, `/nodes/${node}/rows`), { signal });
  if (!response.ok) {
    const text = await response.text();
    const message = errorMessage(text);
    if (response.status === 409) throw new EngineBusyError(message);
    throw new Error(message);
  }
  if (response.body === null) throw new Error("resposta de tuplas sem corpo");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      const batch = checkedRows(
        lines
          .filter((line) => line.length > 0)
          .map((line) => JSON.parse(line) as unknown),
      );
      if (batch.length > 0) yield batch;
    }
    buffer += decoder.decode();
    if (buffer.trim().length > 0)
      yield checkedRows([JSON.parse(buffer) as unknown]);
  } finally {
    reader.releaseLock();
  }
}

function checkedRows(lines: unknown[]): FormValue[][] {
  return lines.map((line, index) =>
    protocol.asArray(line, (cell) => checkedValue(cell), `rows[${index}]`),
  );
}

function checkedValue(value: unknown): FormValue {
  if (
    value === null ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  )
    return value;
  if (Array.isArray(value)) return value.map(checkedValue);
  if (typeof value === "object")
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, checkedValue(item)]),
    );
  throw new Error("tupla malformada na resposta");
}

function listOf<Item>(item: (entry: unknown) => Item) {
  return (json: unknown) => protocol.asArray(json, (entry) => item(entry));
}

export const api = {
  bootstrap: async (): Promise<Bootstrap> => {
    const bootstrap = await request(protocol.bootstrap, "/bootstrap");
    apiToken = bootstrap.token;
    return bootstrap;
  },
  session: (sid: string) => request(protocol.sessionView, ws(sid, "/session")),
  commands: (sid: string, command: Command | object) =>
    request(
      protocol.ack,
      ws(sid, "/commands"),
      post(protocol.command(command)),
    ),
  undo: (sid: string) => request(protocol.ack, ws(sid, "/undo"), post()),
  redo: (sid: string) => request(protocol.ack, ws(sid, "/redo"), post()),
  roots: (sid: string) => request(listOf(protocol.asNumber), ws(sid, "/roots")),
  problems: (sid: string) =>
    request(listOf(protocol.problem), ws(sid, "/problems")),
  rows: (sid: string, node: NodeId, offset: number, limit: number) =>
    request(
      protocol.rowsPage,
      ws(sid, `/nodes/${node}/rows?offset=${offset}&limit=${limit}`),
    ),
  rowsAllStream: (sid: string, node: NodeId, signal?: AbortSignal) =>
    rowsAllStream(sid, node, signal),
  schema: (sid: string, node: NodeId) =>
    request(listOf(protocol.schemaColumn), ws(sid, `/nodes/${node}/schema`)),
  exportUrl: (
    sid: string,
    node: NodeId,
    format: "csv" | "sql",
    table?: string,
  ) => {
    const named = table ? `&table=${encodeURIComponent(table)}` : "";
    return ws(sid, `/nodes/${node}/export?format=${format}${named}`);
  },
  listSessions: () => request(listOf(protocol.sessionMeta), "/sessions"),
  newSession: () => request(protocol.sessionMeta, "/sessions", post()),
  openSession: (path: string) =>
    request(protocol.sessionMeta, "/sessions/open", post({ path })),
  saveSession: (sid: string, name?: string) =>
    request(protocol.sessionMeta, ws(sid, "/save"), post(name ? { name } : {})),
  renameSession: (sid: string, name: string) =>
    request(protocol.sessionMeta, ws(sid, "/rename"), post({ name })),
  closeSession: (sid: string) => request(nothing, ws(sid, "/close"), post()),
  operators: () => request(protocol.catalog, "/operators"),
  pickFile: () => request(protocol.pickedFile, "/pick-file", post()),
  csvPreview: (path: string, headerLine: number, separator?: string) =>
    request(
      protocol.csvPreview,
      "/csv-preview",
      post({ path, headerLine, ...(separator ? { separator } : {}) }),
    ),
  xmlPreview: (path: string, rootElement?: string, recordElement?: string) =>
    request(
      protocol.xmlPreview,
      "/xml-preview",
      post({
        path,
        rootElement: rootElement ?? null,
        recordElement: recordElement ?? null,
      }),
    ),
  config: () => request(protocol.config, "/config"),
  chooseDir: () => request(protocol.config, "/config/sessions-dir", post()),
  listFiles: () => request(listOf(protocol.fileEntry), "/files"),
  shutdown: () => request(nothing, "/shutdown", post()),
};
