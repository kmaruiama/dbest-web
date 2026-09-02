import { api } from "../../src/server/api";
import { installCatalog } from "../../src/server/catalog";
import { installFetchBase } from "./harness";

export async function run(base: string): Promise<number> {
  installFetchBase(base);
  await api.bootstrap();

  let failures = 0;
  const covered: string[] = [];

  async function route(
    name: string,
    action: () => Promise<unknown>,
  ): Promise<unknown> {
    try {
      const value = await action();
      covered.push(name);
      return value;
    } catch (caught) {
      failures = failures + 1;
      console.error(
        `FAIL  ${name}: ${caught instanceof Error ? caught.message : String(caught)}`,
      );
      return undefined;
    }
  }

  installCatalog(await api.operators());
  covered.push("GET /operators");
  const meta = (await route("POST /sessions", () => api.newSession())) as {
    sid: string;
  };
  const sid = meta.sid;
  await route("GET /sessions", () => api.listSessions());
  await route("GET /config", () => api.config());
  await route("GET /files", () => api.listFiles());
  await route("POST /commands (addTable)", () =>
    api.commands(sid, {
      "@type": "addTable",
      id: 1,
      spec: {
        "@type": "memory",
        name: "t",
        columns: [
          {
            name: "n",
            type: "INT",
            primaryKey: true,
            nullable: false,
          },
        ],
        rows: [{ n: { int: 7 } }],
      },
    }),
  );
  await route("POST /commands (addNode)", () =>
    api.commands(sid, {
      "@type": "addNode",
      id: 1,
      node: { "@type": "table", table: 1, alias: "t" },
      at: { x: 0, y: 0 },
    }),
  );
  await route("POST /commands (addNode + connect)", () =>
    api.commands(sid, {
      "@type": "batch",
      commands: [
        {
          "@type": "addNode",
          id: 2,
          node: { "@type": "distinct", hashed: true },
          at: { x: 0, y: 120 },
        },
        { "@type": "connect", edge: { from: 1, to: 2, port: "ONLY" } },
      ],
    }),
  );
  await route("POST /commands (move)", () =>
    api.commands(sid, { "@type": "move", id: 2, to: { x: 10, y: 130 } }),
  );
  const view = (await route("GET /session", () => api.session(sid))) as {
    session: {
      nodes: Map<number, unknown>;
    };
  };
  if (view !== undefined && view.session.nodes.size !== 2) {
    failures = failures + 1;
    console.error(
      `FAIL  session mirrors 2 nodes, saw ${view.session.nodes.size}`,
    );
  }
  await route("GET /roots", () => api.roots(sid));
  await route("GET /problems", () => api.problems(sid));
  await route("GET /nodes/{id}/schema", () => api.schema(sid, 2));
  const page = (await route("GET /nodes/{id}/rows", () =>
    api.rows(sid, 2, 0, 10),
  )) as
    | {
        rows: unknown[];
      }
    | undefined;
  if (page !== undefined && page.rows.length !== 1) {
    failures = failures + 1;
    console.error(
      `FAIL  expected 1 row through the scan, saw ${page.rows.length}`,
    );
  }
  const exported = await route("GET /nodes/{id}/export", async () => {
    const response = await fetch(api.exportUrl(sid, 2, "csv"));
    if (!response.ok) throw new Error(`status ${response.status}`);
    return response.text();
  });
  if (typeof exported === "string" && !exported.includes("7")) {
    failures = failures + 1;
    console.error(
      `FAIL  csv export missing its row: ${JSON.stringify(exported)}`,
    );
  }
  await route("POST /undo", () => api.undo(sid));
  await route("POST /redo", () => api.redo(sid));
  const saved = (await route("POST /sessions/{sid}/save", () =>
    api.saveSession(sid, "parity-sweep"),
  )) as
    | {
        file: string | null;
      }
    | undefined;
  if (saved !== undefined && saved.file !== null) {
    const reopened = (await route("POST /sessions/open", () =>
      api.openSession(saved.file!),
    )) as
      | {
          sid: string;
        }
      | undefined;
    if (reopened !== undefined)
      await route("POST /sessions/{sid}/close (reopened)", () =>
        api.closeSession(reopened.sid),
      );
  }
  await route("POST /sessions/{sid}/close", () => api.closeSession(sid));

  console.log(`  routes: covered ${covered.length} (${covered.join(", ")})`);
  console.log(
    "  routes: not exercised — POST /pick-file, POST /config/sessions-dir (native dialog on the host)",
  );
  return failures;
}
