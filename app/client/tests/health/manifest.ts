import type { FixtureName } from "./fixtures";
import type { Edit } from "./pages/form";

export type Step =
  | {
      as: string;
      table: FixtureName;
      alias: string;
    }
  | {
      as: string;
      chip: string;
      inputs: string[];
      edits?: Edit[];
    };

export type HealthCase = {
  chip: string;
  name: string;
  tables: FixtureName[];
  steps: Step[];
  root: string;
  schema?: string[];
  show?: string[];
  expect: string[];
  ordered?: boolean;
  broken?: string;
};

const table = (as: string, name: FixtureName, alias: string): Step => ({
  as,
  table: name,
  alias,
});
const op = (
  as: string,
  chip: string,
  inputs: string[],
  edits?: Edit[],
): Step =>
  edits === undefined ? { as, chip, inputs } : { as, chip, inputs, edits };
const NAMES = ["ana", "bob", "cid", "dan"];
const PAIR = ["e.name", "d.city"];
const INNER = ["ana/sp", "cid/sp", "bob/rj"];
const CROSS = NAMES.flatMap((name) =>
  ["sp", "rj", "bh"].map((city) => `${name}/${city}`),
);
const JOIN_ON: Edit[] = [
  { at: "on", rows: [{ left: "e.dept", right: "d.id" }] },
];
const filterOn = (
  as: string,
  input: string,
  left: string,
  cmp: string,
  right: string,
): Step =>
  op(
    as,
    "filter",
    [input],
    [{ at: "condition", condition: { left, op: cmp, right } }],
  );
const sortOn = (as: string, input: string, column: string): Step =>
  op(
    as,
    "sort",
    [input],
    [{ at: "keys", rows: [{ column, ascending: true }] }],
  );
const overEmp = (chip: string, edits?: Edit[]): Step[] => [
  table("src", "emp", "e"),
  op("subject", chip, ["src"], edits),
];
const overJoin = (chip: string, edits: Edit[] = JOIN_ON): Step[] => [
  table("l0", "emp", "e"),
  table("r0", "dep", "d"),
  sortOn("l1", "l0", "e.dept"),
  sortOn("r1", "r0", "d.id"),
  op("subject", chip, ["l1", "r1"], edits),
];
const overSets = (chip: string, edits?: Edit[]): Step[] => [
  table("l0", "emp", "e"),
  table("r0", "emp", "e"),
  filterOn("l1", "l0", "e.dept", "EQ", "10"),
  filterOn("r1", "r0", "e.id", "GT", "2"),
  op("subject", chip, ["l1", "r1"], edits),
];
const overSides = (chip: string): Step[] => [
  table("l0", "emp", "e"),
  table("r0", "emp", "e"),
  filterOn("l1", "l0", "e.dept", "EQ", "10"),
  filterOn("r1", "r0", "e.dept", "EQ", "99"),
  op("subject", chip, ["l1", "r1"]),
];
const overScans = (chip: string, edits?: Edit[]): Step[] => [
  table("l0", "emp", "e"),
  table("r0", "dep", "d"),
  op("subject", chip, ["l0", "r0"], edits),
];
const joinCase = (
  chip: string,
  expect: string[],
  show: string[] = PAIR,
  broken?: string,
): HealthCase => ({
  chip,
  name: `${chip} correlates the sorted inputs`,
  tables: ["emp", "dep"],
  steps: overJoin(chip),
  root: "subject",
  show,
  expect,
  ...(broken === undefined ? {} : { broken }),
});
const setCase = (chip: string, expect: string[], name: string): HealthCase => ({
  chip,
  name,
  tables: ["emp"],
  steps: overSets(chip),
  root: "subject",
  show: ["e.name"],
  expect,
});

export const CASES: HealthCase[] = [
  {
    chip: "filter",
    name: "filter keeps the rows that satisfy the condition",
    tables: ["emp"],
    steps: overEmp("filter", [
      {
        at: "condition",
        condition: { left: "e.dept", op: "EQ", right: "10" },
      },
    ]),
    root: "subject",
    schema: ["e.id", "e.name", "e.dept", "e.tags"],
    show: ["e.name"],
    expect: ["ana", "cid"],
  },
  {
    chip: "filter",
    name: "filter on a primary-key column",
    tables: ["empKey"],
    steps: [
      table("src", "empKey", "k"),
      op(
        "subject",
        "filter",
        ["src"],
        [
          {
            at: "condition",
            condition: { left: "k.id", op: "EQ", right: "1" },
          },
        ],
      ),
    ],
    root: "subject",
    show: ["k.name"],
    expect: ["ana"],
    broken:
      "500: IndexScan swallows MemoryTable's unimplemented PK lookup and then reads a null iterator",
  },
  {
    chip: "projection",
    name: "projection keeps only the named columns",
    tables: ["emp"],
    steps: overEmp("projection", [{ at: "columns", list: ["e.name"] }]),
    root: "subject",
    schema: ["e.name"],
    show: ["e.name"],
    expect: NAMES,
  },
  {
    chip: "selectColumns",
    name: "column selection drops the named columns and re-qualifies the rest",
    tables: ["emp"],
    steps: overEmp("selectColumns", [
      { at: "columns", list: ["e.tags"] },
      { at: "alias", text: "P" },
    ]),
    root: "subject",
    schema: ["P.id", "P.name", "P.dept"],
    show: ["P.name"],
    expect: NAMES,
  },
  {
    chip: "rename",
    name: "rename re-qualifies a source",
    tables: ["emp"],
    steps: overEmp("rename", [
      { at: "from", value: "e" },
      { at: "to", text: "x" },
    ]),
    root: "subject",
    schema: ["x.id", "x.name", "x.dept", "x.tags"],
    show: ["x.name"],
    expect: NAMES,
  },
  {
    chip: "rename",
    name: "rename applied to a column, which is what the form offers",
    tables: ["emp"],
    steps: overEmp("rename", [
      { at: "from", value: "e.name" },
      { at: "to", text: "nome" },
    ]),
    root: "subject",
    schema: ["e.id", "e.nome", "e.dept", "e.tags"],
    show: ["e.nome"],
    expect: NAMES,
    broken:
      "the catalog types rename.from as a COLUMN, but the operator renames a source: a column name is silently ignored",
  },
  {
    chip: "sort",
    name: "sort orders by the key",
    tables: ["emp"],
    steps: overEmp("sort", [
      { at: "keys", rows: [{ column: "e.dept", ascending: true }] },
    ]),
    root: "subject",
    show: ["e.name"],
    ordered: true,
    expect: ["ana", "cid", "bob", "dan"],
  },
  {
    chip: "limit",
    name: "limit takes a window of its input",
    tables: ["emp"],
    steps: overEmp("limit", [
      { at: "count", int: 2 },
      { at: "offset", int: 1 },
    ]),
    root: "subject",
    show: ["e.name"],
    ordered: true,
    expect: ["bob", "cid"],
  },
  {
    chip: "limit",
    name: "limit takes a window when it is not the node being run",
    tables: ["emp"],
    steps: [
      table("src", "emp", "e"),
      op(
        "subject",
        "limit",
        ["src"],
        [
          { at: "count", int: 2 },
          { at: "offset", int: 1 },
        ],
      ),
      op(
        "top",
        "projection",
        ["subject"],
        [{ at: "columns", list: ["e.name"] }],
      ),
    ],
    root: "top",
    show: ["e.name"],
    ordered: true,
    expect: ["bob", "cid"],
  },
  {
    chip: "duplicateRemoval",
    name: "duplicate removal collapses equal neighbours in sorted input",
    tables: ["emp"],
    steps: [
      table("src", "emp", "e"),
      sortOn("sorted", "src", "e.dept"),
      op(
        "only",
        "projection",
        ["sorted"],
        [{ at: "columns", list: ["e.dept"] }],
      ),
      op("subject", "duplicateRemoval", ["only"]),
    ],
    root: "subject",
    show: ["e.dept"],
    ordered: true,
    expect: ["10", "20", "40"],
  },
  {
    chip: "hashDuplicateRemoval",
    name: "hash duplicate removal collapses equal rows anywhere in the input",
    tables: ["emp"],
    steps: [
      table("src", "emp", "e"),
      op("only", "projection", ["src"], [{ at: "columns", list: ["e.dept"] }]),
      op("subject", "hashDuplicateRemoval", ["only"]),
    ],
    root: "subject",
    show: ["e.dept"],
    expect: ["10", "20", "40"],
  },
  {
    chip: "aggregation",
    name: "aggregation counts per group",
    tables: ["emp"],
    steps: overEmp("aggregation", [
      { at: "alias", text: "g" },
      { at: "by", value: "e.dept" },
      { at: "aggregates", rows: [{ function: "COUNT", column: "e.id" }] },
      { at: "hashed", flag: true },
    ]),
    root: "subject",
    show: ["g.dept", "g.COUNT_id"],
    expect: ["10/2", "20/1", "40/1"],
  },
  {
    chip: "aggregation",
    name: "aggregation without a group counts the whole input",
    tables: ["emp"],
    steps: overEmp("aggregation", [
      { at: "alias", text: "g" },
      { at: "by", value: "" },
      {
        at: "aggregates",
        rows: [{ function: "COUNT_ALL", column: "e.id" }],
      },
      { at: "hashed", flag: true },
    ]),
    root: "subject",
    show: ["g.COUNT_ALL_id"],
    expect: ["4"],
    broken:
      "ungrouped aggregation returns null for COUNT_ALL, COUNT_NULL, FIRST and LAST (grouped is fine)",
  },
  {
    chip: "collapse",
    name: "collapse merges every source under one alias",
    tables: ["emp", "dep"],
    steps: [
      table("l0", "emp", "e"),
      table("r0", "dep", "d"),
      op("pairs", "cartesianProduct", ["l0", "r0"]),
      op("subject", "collapse", ["pairs"], [{ at: "alias", text: "tudo" }]),
    ],
    root: "subject",
    show: ["tudo.name", "tudo.city"],
    expect: CROSS,
  },
  {
    chip: "explode",
    name: "explode splits a delimited column into one row per part",
    tables: ["emp"],
    steps: overEmp("explode", [
      { at: "column", value: "e.tags" },
      { at: "delimiter", text: ";" },
    ]),
    root: "subject",
    show: ["e.name", "e.tags"],
    expect: ["ana/x", "ana/y", "bob/z", "cid/w", "dan/q"],
  },
  {
    chip: "autoInc",
    name: "row number counts the tuples from its start",
    tables: ["emp"],
    steps: overEmp("autoInc", [
      { at: "alias", text: "r" },
      { at: "column", text: "n" },
      { at: "start", int: 1 },
    ]),
    root: "subject",
    show: ["r.n", "e.name"],
    ordered: true,
    expect: ["1/ana", "2/bob", "3/cid", "4/dan"],
  },
  ...["hash", "memoize", "materialization"].map((chip): HealthCase => ({
    chip,
    name: `${chip} passes its input through unchanged`,
    tables: ["emp"],
    steps: overEmp(chip),
    root: "subject",
    schema: ["e.id", "e.name", "e.dept", "e.tags"],
    show: ["e.name"],
    ordered: true,
    expect: NAMES,
  })),
  {
    chip: "scan",
    name: "scan suppresses the primary-key lookup a filter above it would delegate",
    tables: ["empKey"],
    steps: [
      table("src", "empKey", "k"),
      op("scanned", "scan", ["src"]),
      filterOn("subject", "scanned", "k.id", "EQ", "1"),
    ],
    root: "subject",
    show: ["k.name"],
    expect: ["ana"],
  },
  joinCase("join", INNER),
  joinCase("mergeJoin", INNER),
  joinCase("hashJoin", INNER),
  joinCase("leftOuterJoin", [...INNER, "dan/-"]),
  joinCase("hashLeftOuterJoin", [...INNER, "dan/-"]),
  joinCase(
    "mergeLeftOuterJoin",
    [...INNER, "dan/-"],
    PAIR,
    "500: MergeLeftOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
  ),
  joinCase("rightOuterJoin", [...INNER, "-/bh"]),
  joinCase("hashRightOuterJoin", [...INNER, "-/bh"]),
  joinCase(
    "mergeRightOuterJoin",
    [...INNER, "-/bh"],
    PAIR,
    "500: MergeRightOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
  ),
  joinCase("hashFullOuterJoin", [...INNER, "dan/-", "-/bh"]),
  joinCase(
    "mergeFullOuterJoin",
    [...INNER, "dan/-", "-/bh"],
    PAIR,
    "500: MergeFullOuterJoin reads past its data sources (Index 2 out of bounds for length 2)",
  ),
  {
    chip: "cartesianProduct",
    name: "cartesian product pairs every row with every row",
    tables: ["emp", "dep"],
    steps: overScans("cartesianProduct"),
    root: "subject",
    show: PAIR,
    expect: CROSS,
  },
  {
    chip: "join",
    name: "nested loop join onto a primary key",
    tables: ["emp", "depKey"],
    steps: [
      table("l0", "emp", "e"),
      table("r0", "depKey", "k"),
      op(
        "subject",
        "join",
        ["l0", "r0"],
        [{ at: "on", rows: [{ left: "e.dept", right: "k.id" }] }],
      ),
    ],
    root: "subject",
    show: ["e.name", "k.city"],
    expect: INNER,
    broken:
      "500: the same MemoryTable PK lookup gap, reached through the join's delegated filter",
  },
  joinCase("semiJoin", ["ana", "cid", "bob"], ["e.name"]),
  joinCase("hashLeftSemiJoin", ["ana", "cid", "bob"], ["e.name"]),
  joinCase(
    "mergeLeftSemiJoin",
    ["ana", "cid", "bob"],
    ["e.name"],
    "MergeLeftSemiJoin emits one row per key, losing the second row of a repeated key (cid)",
  ),
  joinCase("hashRightSemiJoin", ["sp", "rj"], ["d.city"]),
  joinCase("mergeRightSemiJoin", ["sp", "rj"], ["d.city"]),
  joinCase("antiJoin", ["dan"], ["e.name"]),
  joinCase("hashLeftAntiJoin", ["dan"], ["e.name"]),
  joinCase(
    "mergeLeftAntiJoin",
    ["dan"],
    ["e.name"],
    "MergeLeftAntiJoin returns a matched row (cid) and drops the unmatched one (dan)",
  ),
  joinCase("hashRightAntiJoin", ["bh"], ["d.city"]),
  joinCase("mergeRightAntiJoin", ["bh"], ["d.city"]),
  setCase(
    "append",
    ["ana", "cid", "cid", "dan"],
    "append concatenates both sides, duplicates and all",
  ),
  setCase("union", ["ana", "cid", "dan"], "union keeps one copy of each row"),
  setCase(
    "hashUnion",
    ["ana", "cid", "dan"],
    "hash union keeps one copy of each row",
  ),
  {
    chip: "union",
    name: "union keeps the source qualifier its inputs carry",
    tables: ["emp"],
    steps: overSets("union"),
    root: "subject",
    schema: ["e.id", "e.name", "e.dept", "e.tags"],
    show: ["e.name"],
    expect: ["ana", "cid", "dan"],
  },
  setCase(
    "intersection",
    ["cid"],
    "intersection keeps the rows both sides have",
  ),
  setCase(
    "hashIntersection",
    ["cid"],
    "hash intersection keeps the rows both sides have",
  ),
  setCase(
    "difference",
    ["ana"],
    "difference keeps the left rows the right side lacks",
  ),
  setCase(
    "hashDifference",
    ["ana"],
    "hash difference keeps the left rows the right side lacks",
  ),
  {
    chip: "unilateralExistence",
    name: "unilateral existence answers with one witness from the left",
    tables: ["emp", "dep"],
    steps: overScans("unilateralExistence"),
    root: "subject",
    show: PAIR,
    expect: ["ana/-"],
  },
  {
    chip: "bilateralExistence",
    name: "bilateral existence answers with one witness from each side",
    tables: ["emp", "dep"],
    steps: overScans("bilateralExistence"),
    root: "subject",
    show: PAIR,
    expect: ["ana/sp"],
  },
  {
    chip: "logicalAnd",
    name: "and is false when one side has no rows",
    tables: ["emp"],
    steps: overSides("logicalAnd"),
    root: "subject",
    show: ["condition.EVAL"],
    expect: ["false"],
  },
  {
    chip: "logicalAnd",
    name: "and is true when both sides have rows",
    tables: ["emp"],
    steps: [
      table("l0", "emp", "e"),
      table("r0", "emp", "e"),
      filterOn("l1", "l0", "e.dept", "EQ", "10"),
      filterOn("r1", "r0", "e.dept", "EQ", "20"),
      op("subject", "logicalAnd", ["l1", "r1"]),
    ],
    root: "subject",
    show: ["condition.EVAL"],
    expect: ["true"],
  },
  {
    chip: "logicalOr",
    name: "or is true when one side has rows",
    tables: ["emp"],
    steps: overSides("logicalOr"),
    root: "subject",
    show: ["condition.EVAL"],
    expect: ["true"],
  },
  {
    chip: "logicalXor",
    name: "xor counts a bare source as satisfied, so it cannot tell these two apart",
    tables: ["emp"],
    steps: overSides("logicalXor"),
    root: "subject",
    show: ["condition.EVAL"],
    expect: ["false"],
  },
];
