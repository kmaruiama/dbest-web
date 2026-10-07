import type { APIRequestContext } from "@playwright/test";
import { copyFileSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { BACKEND_DIR, BACKEND_URL, STATE_FILE, sessionsDirOf } from "../env";
import { FIXTURES, TABLE_IDS, type FixtureName } from "../fixtures";

export type Session = {
  sid: string;
  name: string;
};

let cachedToken: string | null = null;

async function tokenHeader(
  request: APIRequestContext,
): Promise<Record<string, string>> {
  if (cachedToken === null) {
    const response = await request.get(`${BACKEND_URL}/bootstrap`);
    if (!response.ok())
      throw new Error(
        `GET /bootstrap -> ${response.status()} ${await response.text()}`,
      );
    cachedToken = (
      (await response.json()) as {
        token: string;
      }
    ).token;
  }
  return { "X-DBest-Token": cachedToken };
}

async function post(
  request: APIRequestContext,
  path: string,
  body?: unknown,
): Promise<unknown> {
  const response = await request.post(`${BACKEND_URL}${path}`, {
    headers: await tokenHeader(request),
    ...(body === undefined ? {} : { data: body }),
  });
  if (!response.ok()) {
    throw new Error(
      `POST ${path} -> ${response.status()} ${await response.text()}`,
    );
  }
  return response.json();
}

export async function openSession(
  request: APIRequestContext,
  name: string,
  tables: FixtureName[],
): Promise<Session> {
  const created = (await post(request, "/sessions", { name })) as {
    sid: string;
  };
  const sid = created.sid;
  if (tables.length > 0) {
    await post(request, `/sessions/${sid}/commands`, {
      "@type": "batch",
      commands: tables.map((table) => ({
        "@type": "addTable",
        id: TABLE_IDS[table],
        spec: FIXTURES[table],
      })),
    });
  }
  return { sid, name };
}

export async function openSessionFile(
  request: APIRequestContext,
  file: string,
  name: string,
): Promise<Session> {
  const { home } = JSON.parse(readFileSync(STATE_FILE, "utf8")) as {
    home: string;
  };
  const copy = join(sessionsDirOf(home), `${name}.dbest`);
  copyFileSync(join(BACKEND_DIR, "test", "sessions", `${file}.dbest`), copy);
  const opened = (await post(request, "/sessions/open", { path: copy })) as {
    sid: string;
  };
  return { sid: opened.sid, name };
}

export async function closeSession(
  request: APIRequestContext,
  sid: string,
): Promise<void> {
  await request
    .post(`${BACKEND_URL}/sessions/${sid}/close`, {
      headers: await tokenHeader(request),
    })
    .catch(() => undefined);
}

export async function closeAllSessions(
  request: APIRequestContext,
): Promise<void> {
  const response = await request.get(`${BACKEND_URL}/sessions`);
  if (!response.ok()) return;
  const open = (await response.json()) as {
    sid: string;
  }[];
  for (const entry of open) await closeSession(request, entry.sid);
}
