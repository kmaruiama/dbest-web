import type {
  Catalog,
  Columns,
  Command,
  Config,
  CsvGuess,
  FileEntry,
  FormValue,
  Measure,
  NodeId,
  Picked,
  Problem,
  RowsPage,
  SchemaColumn,
  SessionMeta,
  SessionView,
  XmlGuess,
} from "./types";

export class EngineBusyError extends Error {}

export function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export type Api = ReturnType<typeof apiWith>;

export async function connect(): Promise<Api> {
  const { token } = await request<{ token: string }>(null, "/bootstrap");
  return apiWith(token);
}

function apiWith(token: string) {
  const get = <Value>(path: string) => request<Value>(token, path);
  const post = <Value>(path: string, body?: unknown) =>
    request<Value>(
      token,
      path,
      body === undefined
        ? { method: "POST" }
        : { method: "POST", body: JSON.stringify(body) },
    );
  const writes = new Map<string, Promise<unknown>>();
  const inOrder = <Value>(sid: string, write: () => Promise<Value>) => {
    const next = (writes.get(sid) ?? Promise.resolve()).then(write, write);
    writes.set(
      sid,
      next.catch(() => undefined),
    );
    return next;
  };
  return {
    session: (sid: string) => get<SessionView>(sessionPath(sid, "/session")),
    commands: (sid: string, command: Command) =>
      inOrder(sid, () =>
        post<SessionView>(sessionPath(sid, "/commands"), command),
      ),
    undo: (sid: string) =>
      inOrder(sid, () => post<SessionView>(sessionPath(sid, "/undo"))),
    redo: (sid: string) =>
      inOrder(sid, () => post<SessionView>(sessionPath(sid, "/redo"))),
    rename: (sid: string, node: NodeId, alias: string) =>
      inOrder(sid, () =>
        post<SessionView>(nodePath(sid, node, "/alias"), { alias }),
      ),
    problems: (sid: string) => get<Problem[]>(sessionPath(sid, "/problems")),
    rows: (sid: string, node: NodeId, offset: number, limit: number) =>
      get<RowsPage>(
        nodePath(sid, node, `/rows?offset=${offset}&limit=${limit}`),
      ),
    rowsAllStream,
    schema: (sid: string, node: NodeId) =>
      get<SchemaColumn[]>(nodePath(sid, node, "/schema")),
    columns: (sid: string, node: NodeId) =>
      get<Columns>(nodePath(sid, node, "/columns")),
    compare: (
      sid: string,
      node: NodeId,
      other: NodeId,
      limit: number | null,
    ) => {
      const limited = limit === null ? "" : `?limit=${limit}`;
      return get<Measure[]>(nodePath(sid, node, `/compare/${other}${limited}`));
    },
    exportUrl: (
      sid: string,
      node: NodeId,
      format: "csv" | "sql",
      table?: string,
    ) => {
      const named = table ? `&table=${encodeURIComponent(table)}` : "";
      return nodePath(sid, node, `/export?format=${format}${named}`);
    },
    listSessions: () => get<SessionMeta[]>("/sessions"),
    newSession: (name: string) => post<SessionMeta>("/sessions", { name }),
    openSession: (path: string) =>
      post<SessionMeta>("/sessions/open", { path }),
    renameSession: (sid: string, name: string) =>
      post<SessionMeta>(sessionPath(sid, "/rename"), { name }),
    closeSession: (sid: string) =>
      post<SessionMeta[]>(sessionPath(sid, "/close")),
    operators: () => get<Catalog>("/operators"),
    pickFile: () => post<Picked>("/pick-file"),
    csvPreview: (path: string, headerLine: number, separator?: string) =>
      post<CsvGuess>("/csv-preview", {
        path,
        headerLine,
        ...(separator ? { separator } : {}),
      }),
    xmlPreview: (path: string, rootElement?: string, recordElement?: string) =>
      post<XmlGuess>("/xml-preview", {
        path,
        rootElement: rootElement ?? null,
        recordElement: recordElement ?? null,
      }),
    config: () => get<Config>("/config"),
    chooseDir: () => post<Config>("/config/sessions-dir"),
    listFiles: () => get<FileEntry[]>("/files"),
    shutdown: () => post<void>("/shutdown"),
  };
}

async function request<Value>(
  token: string | null,
  path: string,
  init?: RequestInit,
): Promise<Value> {
  const headers = new Headers(init?.headers);
  if (init?.body) headers.set("Content-Type", "application/json");
  if (token !== null) headers.set("X-DBest-Token", token);
  const response = await fetch(path, { ...init, headers });
  const text = await response.text();
  if (!response.ok) {
    console.error(`${init?.method ?? "GET"} ${path} -> ${response.status}`);
    throw failure(response.status, text);
  }
  return (text ? JSON.parse(text) : undefined) as Value;
}

function failure(status: number, text: string): Error {
  const message = errorMessage(text);
  return status === 409 ? new EngineBusyError(message) : new Error(message);
}

function errorMessage(text: string): string {
  try {
    const body = JSON.parse(text) as { error?: unknown };
    return typeof body.error === "string" ? body.error : text;
  } catch {
    return text;
  }
}

function sessionPath(sid: string, path: string): string {
  return `/sessions/${sid}${path}`;
}

function nodePath(sid: string, node: NodeId, path: string): string {
  return sessionPath(sid, `/nodes/${node}${path}`);
}

async function* rowsAllStream(
  sid: string,
  node: NodeId,
  signal?: AbortSignal,
): AsyncGenerator<FormValue[][]> {
  const response = await fetch(nodePath(sid, node, "/rows"), { signal });
  if (!response.ok) throw failure(response.status, await response.text());
  if (response.body === null) throw new Error("resposta de tuplas sem corpo");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += done
        ? decoder.decode()
        : decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = done ? "" : (lines.pop() ?? "");
      const rows = rowsOf(lines);
      if (rows.length > 0) yield rows;
      if (done) break;
    }
  } finally {
    reader.releaseLock();
  }
}

function rowsOf(lines: string[]): FormValue[][] {
  const parsed = lines
    .filter((line) => line.trim().length > 0)
    .map((line) => JSON.parse(line) as FormValue[] | { error: string });
  const rows: FormValue[][] = [];
  for (const line of parsed) {
    if (!Array.isArray(line)) throw new Error(line.error);
    rows.push(line);
  }
  return rows;
}
