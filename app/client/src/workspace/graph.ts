import { inputPorts } from "../server/catalog";
import type { Edge, NodeId, Port, Session } from "../server/types";

export function parentsOf(session: Session, id: NodeId): NodeId[] {
  const parents: NodeId[] = [];
  for (const edge of session.edges) {
    if (edge.to === id) parents.push(edge.from);
  }
  return parents;
}

function childrenOf(session: Session, id: NodeId): NodeId[] {
  const children: NodeId[] = [];
  for (const edge of session.edges) {
    if (edge.from === id) children.push(edge.to);
  }
  return children;
}

export function freePorts(session: Session, id: NodeId): Port[] {
  const node = session.nodes.get(id);
  if (node === undefined) return [];
  const taken = new Set<Port>();
  for (const edge of session.edges) {
    if (edge.to === id) taken.add(edge.port);
  }
  return inputPorts(node.kind).filter((port) => !taken.has(port));
}

export function hasAllInputPortsConnected(
  session: Session,
  id: NodeId,
): boolean {
  const node = session.nodes.get(id);
  return (
    node !== undefined &&
    inputPorts(node.kind).length > 0 &&
    freePorts(session, id).length === 0
  );
}

export function reaches(
  session: Session,
  start: NodeId,
  target: NodeId,
): boolean {
  const seen = new Set<NodeId>([start]);
  const pending = [start];
  while (pending.length > 0) {
    const current = pending.pop();
    if (current === undefined) continue;
    for (const next of childrenOf(session, current)) {
      if (next === target) return true;
      if (seen.has(next)) continue;
      seen.add(next);
      pending.push(next);
    }
  }
  return false;
}

function linksFrom(session: Session, from: NodeId, to: NodeId): Edge[] {
  if (from === to || !session.nodes.has(from)) return [];
  const ports = freePorts(session, to);
  if (ports.length === 0) return [];
  if (reaches(session, to, from)) return [];
  return ports.map((port) => ({ from, to, port }));
}

export function legalLinks(
  session: Session,
  first: NodeId,
  second: NodeId,
): Edge[] {
  return [
    ...linksFrom(session, first, second),
    ...linksFrom(session, second, first),
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
  session: Session,
  first: NodeId,
  second: NodeId,
): Resolution {
  const forward = linksFrom(session, first, second);
  if (forward.length === 1) return { kind: "one", link: forward[0] };
  if (forward.length > 1) return { kind: "many", options: forward };
  const backward = linksFrom(session, second, first);
  if (backward.length === 1) return { kind: "one", link: backward[0] };
  if (backward.length > 1) return { kind: "many", options: backward };
  return { kind: "none", reason: blockedReason(session, first, second) };
}

export function blockedReason(
  session: Session,
  first: NodeId,
  second: NodeId,
): string {
  if (first === second) return "link.sameNode";
  if (
    freePorts(session, first).length === 0 &&
    freePorts(session, second).length === 0
  ) {
    return "link.bothFull";
  }
  return "link.cycle";
}

export function linkTargets(session: Session, from: NodeId): Set<NodeId> {
  const targets = new Set<NodeId>();
  for (const id of session.nodes.keys()) {
    if (legalLinks(session, from, id).length > 0) targets.add(id);
  }
  return targets;
}
