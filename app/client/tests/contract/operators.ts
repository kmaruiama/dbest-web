import { withField } from "../../src/form/values";
import { api } from "../../src/server/api";
import {
  chipByKey,
  inputPorts,
  installCatalog,
} from "../../src/server/catalog";
import { showRawValue } from "../../src/server/literals";
import type { FormValue } from "../../src/form/FormDraft";
import type { PlanNode } from "../../src/server/types";
import { installFetchBase } from "./harness";

type Wire = Record<string, unknown>;

const int = (value: number) => ({ int: value });
const str = (value: string) => ({ str: value });
const column = (name: string, type: string, primaryKey = false) => ({
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
const FIXTURES: Record<string, Wire> = {
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
const TABLE_IDS: Record<string, number> = {
  emp: 1,
  dep: 2,
  empKey: 3,
  depKey: 4,
};

type Build = {
  commands: Wire[];
  table: (name: keyof typeof TABLE_IDS, alias: string) => number;
  add: (node: PlanNode | Wire, ...inputs: number[]) => number;
  sorted: (input: number, on: string) => number;
  filter: (input: number, condition: unknown) => number;
};

function toWire(node: PlanNode | Wire): Wire {
  return "fields" in node
    ? { "@type": (node as PlanNode).kind, ...(node as PlanNode).fields }
    : (node as Wire);
}

function builder(): Build {
  const commands: Wire[] = [];
  let minted = 0;
  const place = (node: PlanNode | Wire, inputs: number[]): number => {
    minted = minted + 1;
    const id = minted;
    const wire = toWire(node);
    commands.push({
      "@type": "addNode",
      id,
      node: wire,
      at: { x: id * 70, y: id * 50 },
    });
    const ports = inputPorts(String(wire["@type"]));
    if (ports.length !== inputs.length) {
      throw new Error(
        `${wire["@type"]} takes ${ports.length} input(s), the case gave ${inputs.length}`,
      );
    }
    ports.forEach((port, index) =>
      commands.push({
        "@type": "connect",
        edge: { from: inputs[index], to: id, port },
      }),
    );
    return id;
  };
  return {
    commands,
    table: (name, alias) =>
      place({ "@type": "table", table: TABLE_IDS[name], alias }, []),
    add: (node, ...inputs) => place(node, inputs),
    sorted: (input, on) =>
      place({ "@type": "sort", keys: [{ column: on, ascending: true }] }, [
        input,
      ]),
    filter: (input, condition) =>
      place({ "@type": "filter", condition }, [input]),
  };
}

const cmp = (source: string, name: string, op: string, right: unknown) => ({
  "@type": "cmp",
  left: { source, name },
  op,
  right,
});

type Case = {
  name: string;
  chip: string;
  fields?: Record<string, unknown>;
  build: (build: Build, node: PlanNode) => number;
  show?: string[];
  keys?: string[];
  expect: string[];
  ordered?: boolean;
  broken?: string;
};

function overJoin(build: Build, node: PlanNode): number {
  const left = build.sorted(build.table("emp", "e"), "e.dept");
  const right = build.sorted(build.table("dep", "d"), "d.id");
  return build.add(node, left, right);
}

function overSets(build: Build, node: PlanNode): number {
  const left = build.filter(
    build.table("emp", "e"),
    cmp("e", "dept", "EQ", int(10)),
  );
  const right = build.filter(
    build.table("emp", "e"),
    cmp("e", "id", "GT", int(2)),
  );
  return build.add(node, left, right);
}

function overSides(build: Build, node: PlanNode): number {
  const left = build.filter(
    build.table("emp", "e"),
    cmp("e", "dept", "EQ", int(10)),
  );
  const right = build.filter(
    build.table("emp", "e"),
    cmp("e", "dept", "EQ", int(99)),
  );
  return build.add(node, left, right);
}

const NAMES = ["ana", "bob", "cid", "dan"];
const JOIN_ON = [
  {
    left: { source: "e", column: "dept" },
    right: { source: "d", column: "id" },
  },
];
const PAIR = ["e.name", "d.city"];
const INNER = ["ana/sp", "cid/sp", "bob/rj"];
const CASES: Case[] = [
  {
    name: "filter keeps the rows that satisfy the condition",
    chip: "filter",
    fields: { condition: cmp("e", "dept", "EQ", int(10)) },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["e.name"],
    expect: ["ana", "cid"],
  },
  {
    name: "filter on a primary-key column",
    chip: "filter",
    fields: { condition: cmp("k", "id", "EQ", int(1)) },
    build: (build, node) => build.add(node, build.table("empKey", "k")),
    show: ["k.name"],
    expect: ["ana"],
    broken:
      "500: IndexScan swallows MemoryTable's unimplemented PK lookup and then reads a null iterator",
  },
  {
    name: "projection keeps only the named columns",
    chip: "projection",
    fields: { columns: ["e.name"] },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    keys: ["e.name"],
    show: ["e.name"],
    expect: NAMES,
  },
  {
    name: "column selection drops the named columns and re-qualifies the rest",
    chip: "selectColumns",
    fields: { columns: ["e.tags"], alias: "P" },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    keys: ["P.id", "P.name", "P.dept"],
    show: ["P.name"],
    expect: NAMES,
  },
  {
    name: "rename re-qualifies a source",
    chip: "rename",
    fields: { from: "e", to: "x" },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    keys: ["x.id", "x.name", "x.dept", "x.tags"],
    show: ["x.name"],
    expect: NAMES,
  },
  {
    name: "rename applied to a column, which is what the form offers",
    chip: "rename",
    fields: { from: "e.name", to: "nome" },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    keys: ["e.id", "e.nome", "e.dept", "e.tags"],
    show: ["e.nome"],
    expect: NAMES,
    broken:
      "the catalog types rename.from as a COLUMN, but the operator renames a source: a column name is silently ignored",
  },
  {
    name: "sort orders by the key",
    chip: "sort",
    fields: { keys: [{ column: "e.dept", ascending: true }] },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["e.name"],
    ordered: true,
    expect: ["ana", "cid", "bob", "dan"],
  },
  {
    name: "limit takes a window of its input",
    chip: "limit",
    fields: { count: 2, offset: 1 },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["e.name"],
    ordered: true,
    expect: ["bob", "cid"],
  },
  {
    name: "limit takes a window when it is not the node being run",
    chip: "limit",
    fields: { count: 2, offset: 1 },
    build: (build, node) =>
      build.add(
        { "@type": "project", columns: ["e.name"] },
        build.add(node, build.table("emp", "e")),
      ),
    show: ["e.name"],
    ordered: true,
    expect: ["bob", "cid"],
  },
  {
    name: "duplicate removal collapses equal neighbours in sorted input",
    chip: "duplicateRemoval",
    fields: {},
    build: (build, node) => {
      const sorted = build.sorted(build.table("emp", "e"), "e.dept");
      return build.add(
        node,
        build.add({ "@type": "project", columns: ["e.dept"] }, sorted),
      );
    },
    show: ["e.dept"],
    ordered: true,
    expect: ["10", "20", "40"],
  },
  {
    name: "hashed duplicate removal collapses equal rows anywhere in the input",
    chip: "hashDuplicateRemoval",
    fields: {},
    build: (build, node) =>
      build.add(
        node,
        build.add(
          { "@type": "project", columns: ["e.dept"] },
          build.table("emp", "e"),
        ),
      ),
    show: ["e.dept"],
    expect: ["10", "20", "40"],
  },
  {
    name: "aggregation counts per group",
    chip: "aggregation",
    fields: {
      alias: "g",
      by: { source: "e", column: "dept" },
      aggregates: [{ column: "e.id", function: "COUNT" }],
      hashed: true,
    },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["g.dept", "g.COUNT_id"],
    expect: ["10/2", "20/1", "40/1"],
  },
  {
    name: "aggregation without a group counts the whole input",
    chip: "aggregation",
    fields: {
      alias: "g",
      by: null,
      aggregates: [{ column: "e.id", function: "COUNT_ALL" }],
      hashed: true,
    },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["g.COUNT_ALL_id"],
    expect: ["4"],
    broken:
      "ungrouped aggregation returns null for COUNT_ALL, COUNT_NULL, FIRST and LAST (grouped is fine)",
  },
  {
    name: "collapse merges every source under one alias",
    chip: "collapse",
    fields: { alias: "tudo" },
    build: (build, node) =>
      build.add(
        node,
        build.add(
          { "@type": "cross" },
          build.table("emp", "e"),
          build.table("dep", "d"),
        ),
      ),
    show: ["tudo.name", "tudo.city"],
    expect: NAMES.flatMap((name) =>
      ["sp", "rj", "bh"].map((city) => `${name}/${city}`),
    ),
  },
  {
    name: "explode splits a delimited column into one row per part",
    chip: "explode",
    fields: { column: "e.tags", delimiter: ";" },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["e.name", "e.tags"],
    expect: ["ana/x", "ana/y", "bob/z", "cid/w", "dan/q"],
  },
  {
    name: "auto increment numbers the tuples from its start",
    chip: "autoInc",
    fields: { alias: "r", column: "n", start: 1 },
    build: (build, node) => build.add(node, build.table("emp", "e")),
    show: ["r.n", "e.name"],
    ordered: true,
    expect: ["1/ana", "2/bob", "3/cid", "4/dan"],
  },
  ...["hash", "memoize", "materialization"].map((chip) => ({
    name: `${chip} passes its input through unchanged`,
    chip,
    fields: {},
    build: (build: Build, node: PlanNode) =>
      build.add(node, build.table("emp", "e")),
    show: ["e.name"],
    ordered: true,
    expect: NAMES,
  })),
  {
    name: "scan suppresses the primary-key lookup a filter above it would delegate",
    chip: "scan",
    fields: {},
    build: (build, node) =>
      build.filter(
        build.add(node, build.table("empKey", "k")),
        cmp("k", "id", "EQ", int(1)),
      ),
    show: ["k.name"],
    expect: ["ana"],
  },
  ...[
    { chip: "join", expect: INNER },
    { chip: "mergeJoin", expect: INNER },
    { chip: "hashJoin", expect: INNER },
    { chip: "leftOuterJoin", expect: [...INNER, "dan/-"] },
    { chip: "hashLeftOuterJoin", expect: [...INNER, "dan/-"] },
    {
      chip: "mergeLeftOuterJoin",
      expect: [...INNER, "dan/-"],
      broken:
        "500: MergeLeftOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
    },
    { chip: "rightOuterJoin", expect: [...INNER, "-/bh"] },
    { chip: "hashRightOuterJoin", expect: [...INNER, "-/bh"] },
    {
      chip: "mergeRightOuterJoin",
      expect: [...INNER, "-/bh"],
      broken:
        "500: MergeRightOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
    },
    { chip: "hashFullOuterJoin", expect: [...INNER, "dan/-", "-/bh"] },
    {
      chip: "mergeFullOuterJoin",
      expect: [...INNER, "dan/-", "-/bh"],
      broken:
        "500: MergeFullOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
    },
  ].map(({ chip, expect, broken }) => ({
    name: `${chip} correlates the sorted inputs`,
    chip,
    fields: { on: JOIN_ON },
    build: overJoin,
    show: PAIR,
    expect,
    broken,
  })),
  {
    name: "cartesian product pairs every row with every row",
    chip: "cartesianProduct",
    fields: {},
    build: (build, node) =>
      build.add(node, build.table("emp", "e"), build.table("dep", "d")),
    show: PAIR,
    expect: NAMES.flatMap((name) =>
      ["sp", "rj", "bh"].map((city) => `${name}/${city}`),
    ),
  },
  {
    name: "nested loop join onto a primary key",
    chip: "join",
    fields: {
      on: [
        {
          left: { source: "e", column: "dept" },
          right: { source: "k", column: "id" },
        },
      ],
    },
    build: (build, node) =>
      build.add(node, build.table("emp", "e"), build.table("depKey", "k")),
    show: ["e.name", "k.city"],
    expect: INNER,
    broken:
      "500: the same MemoryTable PK lookup gap, reached through the join's delegated filter",
  },
  ...[
    { chip: "semiJoin", show: ["e.name"], expect: ["ana", "cid", "bob"] },
    {
      chip: "hashLeftSemiJoin",
      show: ["e.name"],
      expect: ["ana", "cid", "bob"],
    },
    {
      chip: "mergeLeftSemiJoin",
      show: ["e.name"],
      expect: ["ana", "cid", "bob"],
      broken:
        "MergeLeftSemiJoin emits one row per key, losing the second row of a repeated key (cid)",
    },
    { chip: "hashRightSemiJoin", show: ["d.city"], expect: ["sp", "rj"] },
    { chip: "mergeRightSemiJoin", show: ["d.city"], expect: ["sp", "rj"] },
    { chip: "antiJoin", show: ["e.name"], expect: ["dan"] },
    { chip: "hashLeftAntiJoin", show: ["e.name"], expect: ["dan"] },
    {
      chip: "mergeLeftAntiJoin",
      show: ["e.name"],
      expect: ["dan"],
      broken:
        "MergeLeftAntiJoin returns a matched row (cid) and drops the unmatched one (dan)",
    },
    { chip: "hashRightAntiJoin", show: ["d.city"], expect: ["bh"] },
    { chip: "mergeRightAntiJoin", show: ["d.city"], expect: ["bh"] },
  ].map(({ chip, show, expect, broken }) => ({
    name: `${chip} keeps only the side it is named for`,
    chip,
    fields: { on: JOIN_ON },
    build: overJoin,
    show,
    expect,
    broken,
  })),
  {
    name: "append concatenates both sides, duplicates and all",
    chip: "append",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["ana", "cid", "cid", "dan"],
  },
  {
    name: "union keeps one copy of each row",
    chip: "union",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["ana", "cid", "dan"],
  },
  {
    name: "hashed union keeps one copy of each row",
    chip: "hashUnion",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["ana", "cid", "dan"],
  },
  {
    name: "union keeps the source qualifier its inputs carry",
    chip: "union",
    fields: {},
    build: overSets,
    keys: ["e.id", "e.name", "e.dept", "e.tags"],
    show: ["e.name"],
    expect: ["ana", "cid", "dan"],
  },
  {
    name: "intersection keeps the rows both sides have",
    chip: "intersection",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["cid"],
  },
  {
    name: "hashed intersection keeps the rows both sides have",
    chip: "hashIntersection",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["cid"],
  },
  {
    name: "difference keeps the left rows the right side lacks",
    chip: "difference",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["ana"],
  },
  {
    name: "hashed difference keeps the left rows the right side lacks",
    chip: "hashDifference",
    fields: {},
    build: overSets,
    show: ["e.name"],
    expect: ["ana"],
  },
  {
    name: "unilateral existence answers with one witness from the left",
    chip: "unilateralExistence",
    fields: {},
    build: (build, node) =>
      build.add(node, build.table("emp", "e"), build.table("dep", "d")),
    show: PAIR,
    expect: ["ana/-"],
  },
  {
    name: "bilateral existence answers with one witness from each side",
    chip: "bilateralExistence",
    fields: {},
    build: (build, node) =>
      build.add(node, build.table("emp", "e"), build.table("dep", "d")),
    show: PAIR,
    expect: ["ana/sp"],
  },
  {
    name: "and is false when one side has no rows",
    chip: "logicalAnd",
    fields: {},
    build: overSides,
    show: ["condition.EVAL"],
    expect: ["false"],
  },
  {
    name: "and is true when both sides have rows",
    chip: "logicalAnd",
    fields: {},
    build: (build, node) => {
      const left = build.filter(
        build.table("emp", "e"),
        cmp("e", "dept", "EQ", int(10)),
      );
      const right = build.filter(
        build.table("emp", "e"),
        cmp("e", "dept", "EQ", int(20)),
      );
      return build.add(node, left, right);
    },
    show: ["condition.EVAL"],
    expect: ["true"],
  },
  {
    name: "or is true when one side has rows",
    chip: "logicalOr",
    fields: {},
    build: overSides,
    show: ["condition.EVAL"],
    expect: ["true"],
  },
  {
    name: "xor counts a bare source as satisfied, so it cannot tell these two apart",
    chip: "logicalXor",
    fields: {},
    build: overSides,
    show: ["condition.EVAL"],
    expect: ["false"],
  },
];
function configure(
  template: PlanNode,
  fields: Record<string, unknown>,
): PlanNode {
  let node = template;
  for (const [at, value] of Object.entries(fields)) {
    node = withField(node, at, value as FormValue);
  }
  return node;
}

function render(
  row: unknown[],
  keys: string[],
  show: string[] | undefined,
): string {
  if (show === undefined) {
    return keys
      .map((key, index) => `${key}=${showRawValue(row[index])}`)
      .join(" ");
  }
  return show
    .map((key) => {
      const index = keys.indexOf(key);
      return index === -1 ? "-" : showRawValue(row[index]) || "-";
    })
    .join("/");
}

function same(
  produced: string[],
  expected: string[],
  ordered: boolean,
): boolean {
  const left = ordered ? produced : [...produced].sort();
  const right = ordered ? expected : [...expected].sort();
  return (
    left.length === right.length &&
    left.every((value, index) => value === right[index])
  );
}

type Outcome =
  | {
      ok: true;
    }
  | {
      ok: false;
      why: string;
    };

async function attempt(entry: Case): Promise<Outcome> {
  const chip = chipByKey(entry.chip);
  if (chip === undefined)
    return { ok: false, why: `the catalog has no chip "${entry.chip}"` };
  const node = configure(chip.template, entry.fields ?? {});
  const build = builder();
  const root = entry.build(build, node);
  const { sid } = await api.newSession();
  try {
    await api.commands(sid, {
      "@type": "batch",
      commands: [
        ...Object.entries(FIXTURES).map(([name, spec]): Wire => ({
          "@type": "addTable",
          id: TABLE_IDS[name],
          spec,
        })),
        ...build.commands,
      ],
    });
    const problems = await api.problems(sid);
    if (problems.length > 0) {
      return {
        ok: false,
        why: `the plan is incomplete: ${problems.map((problem) => problem.message).join("; ")}`,
      };
    }
    const schema = await api.schema(sid, root);
    const keys = schema.map((entry) => `${entry.source}.${entry.name}`);
    if (entry.keys !== undefined && !same(keys, entry.keys, true)) {
      return {
        ok: false,
        why: `columns ${JSON.stringify(keys)}, expected ${JSON.stringify(entry.keys)}`,
      };
    }
    const page = await api.rows(sid, root, 0, 100);
    const produced = page.rows.map((row) => render(row, keys, entry.show));
    if (!same(produced, entry.expect, entry.ordered === true)) {
      return {
        ok: false,
        why: `rows ${JSON.stringify(produced)}, expected ${JSON.stringify(entry.expect)}`,
      };
    }
    return { ok: true };
  } catch (caught) {
    return {
      ok: false,
      why: caught instanceof Error ? caught.message : String(caught),
    };
  } finally {
    await api.closeSession(sid).catch(() => undefined);
  }
}

export async function run(base: string): Promise<number> {
  installFetchBase(base);
  await api.bootstrap();

  let failures = 0;
  const brokenStill: string[] = [];
  const fail = (what: string): void => {
    failures = failures + 1;
    console.error(`FAIL  ${what}`);
  };

  const catalog = await api.operators();
  installCatalog(catalog);
  const covered = new Set(CASES.map((entry) => entry.chip));
  for (const chip of catalog.operators) {
    if (!covered.has(chip.key))
      fail(`the palette offers "${chip.key}" and this sweep never runs it`);
  }
  console.log(
    `${catalog.operators.length} chips in the palette, ${CASES.length} cases\n`,
  );
  for (const entry of CASES) {
    const outcome = await attempt(entry);
    if (entry.broken === undefined) {
      if (outcome.ok) console.log(`ok    ${entry.chip}: ${entry.name}`);
      else fail(`${entry.chip}: ${entry.name}\n      ${outcome.why}`);
      continue;
    }
    if (outcome.ok) {
      fail(
        `${entry.chip}: ${entry.name}\n      this is marked broken but now passes — drop the note`,
      );
      continue;
    }
    brokenStill.push(`${entry.chip}: ${entry.broken}\n      ${outcome.why}`);
    console.log(`known ${entry.chip}: ${entry.name}`);
  }
  if (brokenStill.length > 0) {
    console.log(
      `  operators: ${brokenStill.length} known defect(s), still failing as recorded:`,
    );
    for (const note of brokenStill) console.log(`    ${note}`);
  }
  return failures;
}
