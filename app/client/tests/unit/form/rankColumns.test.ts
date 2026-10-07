import { describe, expect, it } from "vitest";
import { rankColumns } from "../../../src/form/rankColumns";

const columns = ["orders.created_at", "customer.id", "id", "total"];

describe("rankColumns", () => {
  const scenarios: { name: string; query: string; ranked: string[] }[] = [
    {
      name: "an exact hit outranks a suffix hit, misses keep their order",
      query: "id",
      ranked: ["id", "customer.id", "orders.created_at", "total"],
    },
    {
      name: "matching is case-insensitive",
      query: "ID",
      ranked: ["id", "customer.id", "orders.created_at", "total"],
    },
    {
      name: "an earlier match beats a later one",
      query: "total",
      ranked: ["total", "orders.created_at", "customer.id", "id"],
    },
    {
      name: "an empty query keeps the catalog order",
      query: "",
      ranked: columns,
    },
    {
      name: "a whitespace query keeps the catalog order",
      query: "   ",
      ranked: columns,
    },
    {
      name: "no match keeps everything in place",
      query: "zzz",
      ranked: columns,
    },
  ];

  it.each(scenarios)("$name", ({ query, ranked }) => {
    expect(rankColumns(columns, query)).toEqual(ranked);
  });

  it("never mutates the source list", () => {
    const snapshot = [...columns];
    rankColumns(columns, "id");
    expect(columns).toEqual(snapshot);
  });
});
