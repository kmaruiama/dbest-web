import { sourceNameOf } from "./nodes";
import type { Session } from "./types";

export function mint(taken: Iterable<number>, last: number): number {
  let highest = last;
  for (const id of taken) {
    if (id > highest) highest = id;
  }
  return highest + 1;
}

export function freshAlias(session: Session, base: string): string {
  const taken = new Set<string>();
  for (const node of Object.values(session.nodes)) {
    const name = sourceNameOf(node);
    if (name !== null) taken.add(name);
  }
  let alias = base;
  for (let suffix = 2; taken.has(alias); suffix++) alias = `${base}${suffix}`;
  return alias;
}
