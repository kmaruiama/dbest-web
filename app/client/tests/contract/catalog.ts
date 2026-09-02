import { api } from "../../src/server/api";
import {
  chipOf,
  fieldsOf,
  inputPorts,
  installCatalog,
  isEditable,
  symbolOf,
  variantOptions,
} from "../../src/server/catalog";
import { showRawValue } from "../../src/server/literals";
import * as wire from "../../src/server/protocol";
import { installFetchBase } from "./harness";

export async function run(base: string): Promise<number> {
  installFetchBase(base);
  await api.bootstrap();

  let failures = 0;
  const check = (ok: boolean, what: string): void => {
    if (!ok) {
      failures = failures + 1;
      console.error(`FAIL  ${what}`);
    }
  };
  const get = async (path: string): Promise<unknown> => {
    const response = await fetch(`${base}${path}`);
    if (!response.ok) throw new Error(`GET ${path} -> ${response.status}`);
    return response.json();
  };

  const catalog = wire.catalog(await get("/operators"));
  installCatalog(catalog);

  for (const chip of catalog.operators) {
    const found = chipOf(chip.template);
    check(
      found !== undefined && found.key === chip.key,
      `chipOf(${chip.key}) -> ${found?.key}`,
    );
  }
  for (const [kind, type] of Object.entries(catalog.types)) {
    const expected =
      type.arity === "source" ? 0 : type.arity === "binary" ? 2 : 1;
    check(
      inputPorts(kind).length === expected,
      `inputPorts(${kind}) for arity ${type.arity}`,
    );
    check(
      isEditable(kind) === fieldsOf(kind).length > 0,
      `editable agrees with fields: ${kind}`,
    );
  }

  const fullMerge = catalog.operators.find(
    (chip) => chip.key === "mergeFullOuterJoin",
  );
  if (fullMerge === undefined) {
    check(false, "expected a mergeFullOuterJoin chip");
  } else {
    const algorithms = variantOptions(fullMerge.template, "algorithm");
    check(algorithms !== null, "join.algorithm is a variant field");
    check(
      !(algorithms ?? []).includes("NESTED_LOOP"),
      `FULL join excludes NESTED_LOOP, got ${JSON.stringify(algorithms)}`,
    );
    check((algorithms ?? []).includes("MERGE"), "FULL join allows MERGE");
  }
  const append = catalog.operators.find((chip) => chip.key === "append");
  if (append !== undefined) {
    const hashed = variantOptions(append.template, "hashed");
    check(
      (hashed ?? []).length === 1 && hashed?.[0] === false,
      `APPEND set-op cannot hash, got ${JSON.stringify(hashed)}`,
    );
  }

  let sessions = (await get("/sessions")) as { sid: string }[];
  let seeded: string | null = null;
  if (sessions.length === 0) {
    const meta = await api.newSession();
    seeded = meta.sid;
    await api.commands(seeded, {
      "@type": "batch",
      commands: [
        {
          "@type": "addTable",
          id: 1,
          spec: {
            "@type": "memory",
            name: "t",
            columns: [
              { name: "n", type: "INT", primaryKey: true, nullable: false },
            ],
            rows: [{ n: { int: 7 } }],
          },
        },
        {
          "@type": "addNode",
          id: 1,
          node: { "@type": "table", table: 1, alias: "t" },
          at: { x: 0, y: 0 },
        },
        {
          "@type": "addNode",
          id: 2,
          node: {
            "@type": "filter",
            condition: {
              "@type": "cmp",
              left: { source: "t", name: "n" },
              op: "GTE",
              right: { int: 1 },
            },
          },
          at: { x: 0, y: 120 },
        },
        { "@type": "connect", edge: { from: 1, to: 2, port: "ONLY" } },
      ],
    });
    sessions = (await get("/sessions")) as { sid: string }[];
  }

  if (sessions.length > 0) {
    const sid = sessions[0].sid;
    const view = wire.sessionView(await get(`/sessions/${sid}/session`));
    for (const [id, node] of view.session.nodes) {
      const caption = view.captions.get(id);
      const glyph = symbolOf(node);
      check(
        caption !== undefined,
        `the server sent no caption for node ${id} (${node.kind})`,
      );
      if (caption === undefined) continue;
      check(
        caption.engineClass !== null,
        `${node.kind} compiles to no engine class`,
      );
      check(glyph.known, `${node.kind} has a symbol`);
    }

    const roots = (await get(`/sessions/${sid}/roots`)) as number[];
    for (const root of roots) {
      const schema = (await get(`/sessions/${sid}/nodes/${root}/schema`).catch(
        () => [],
      )) as {
        source: string;
        name: string;
      }[];
      if (schema.length === 0) continue;
      const page = await get(
        `/sessions/${sid}/nodes/${root}/rows?offset=0&limit=5`,
      )
        .then(wire.rowsPage)
        .catch((caught: unknown) => String(caught));
      if (typeof page === "string" || page.rows.length === 0) continue;
      const headers = schema.map((column) => `${column.source}.${column.name}`);
      const first = page.rows[0];
      check(
        first.length === headers.length,
        `row has one value per schema column (${headers.length})`,
      );
      const rendered = headers
        .map((_, index) => showRawValue(first[index]))
        .join(" | ");
      check(
        rendered.replaceAll("|", "").trim().length > 0,
        "literals render as text",
      );
    }
  }

  if (seeded !== null) await api.closeSession(seeded).catch(() => undefined);
  return failures;
}
