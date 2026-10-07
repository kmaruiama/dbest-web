import { inputPorts } from "./catalog";
import type { Catalog, Edge, NodeId, Port, Session } from "./types";

export function freePorts(
  catalog: Catalog,
  session: Session,
  id: NodeId,
): Port[] {
  const node = session.nodes[id];
  if (node === undefined) return [];
  const taken = new Set<Port>();
  for (const edge of session.edges) {
    if (edge.to === id) taken.add(edge.port);
  }
  return inputPorts(catalog, node["@type"]).filter((port) => !taken.has(port));
}

export function hasAllInputPortsConnected(
  catalog: Catalog,
  session: Session,
  id: NodeId,
): boolean {
  const node = session.nodes[id];
  return (
    node !== undefined &&
    inputPorts(catalog, node["@type"]).length > 0 &&
    freePorts(catalog, session, id).length === 0
  );
}

function walk(
  session: Session,
  start: NodeId,
  direction: "downstream" | "either",
): Set<NodeId> {
  const seen = new Set<NodeId>();
  const pending = [start];
  while (pending.length > 0) {
    const current = pending.pop();
    if (current === undefined || seen.has(current)) continue;
    seen.add(current);
    for (const edge of session.edges) {
      if (edge.from === current) pending.push(edge.to);
      if (direction === "either" && edge.to === current)
        pending.push(edge.from);
    }
  }
  return seen;
}

export function reaches(
  session: Session,
  start: NodeId,
  target: NodeId,
): boolean {
  return start !== target && walk(session, start, "downstream").has(target);
}

export function sameTree(
  session: Session,
  first: NodeId,
  second: NodeId,
): boolean {
  return walk(session, first, "either").has(second);
}

function linksFrom(
  catalog: Catalog,
  session: Session,
  from: NodeId,
  to: NodeId,
): Edge[] {
  if (from === to || !(from in session.nodes)) return [];
  const ports = freePorts(catalog, session, to);
  if (ports.length === 0) return [];
  if (reaches(session, to, from)) return [];
  return ports.map((port) => ({ from, to, port }));
}

export function legalLinks(
  catalog: Catalog,
  session: Session,
  first: NodeId,
  second: NodeId,
): Edge[] {
  return [
    ...linksFrom(catalog, session, first, second),
    ...linksFrom(catalog, session, second, first),
  ];
}

type Resolution =
  | {
      kind: "none";
      reason: string;
    }
  | {
      kind: "one";
      link: Edge;
    }
  | {
      kind: "many";
      options: Edge[];
    };

export function resolveLink(
  catalog: Catalog,
  session: Session,
  first: NodeId,
  second: NodeId,
): Resolution {
  const forward = linksFrom(catalog, session, first, second);
  if (forward.length === 1) return { kind: "one", link: forward[0] };
  if (forward.length > 1) return { kind: "many", options: forward };
  const backward = linksFrom(catalog, session, second, first);
  if (backward.length === 1) return { kind: "one", link: backward[0] };
  if (backward.length > 1) return { kind: "many", options: backward };
  return {
    kind: "none",
    reason: blockedReason(catalog, session, first, second),
  };
}

export function blockedReason(
  catalog: Catalog,
  session: Session,
  first: NodeId,
  second: NodeId,
): string {
  if (first === second) return "link.sameNode";
  if (
    freePorts(catalog, session, first).length === 0 &&
    freePorts(catalog, session, second).length === 0
  ) {
    return "link.bothFull";
  }
  return "link.cycle";
}

export function linkTargets(
  catalog: Catalog,
  session: Session,
  from: NodeId,
): Set<NodeId> {
  const targets = new Set<NodeId>();
  for (const id of Object.keys(session.nodes).map(Number)) {
    if (legalLinks(catalog, session, from, id).length > 0) targets.add(id);
  }
  return targets;
}
