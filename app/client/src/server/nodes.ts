import type { Node } from "./types";

export function aliasOf(node: Node): string | null {
  return typeof node.alias === "string" ? node.alias : null;
}

export function sourceNameOf(node: Node): string | null {
  if (node["@type"] === "alias")
    return typeof node.to === "string" ? node.to : null;
  return aliasOf(node);
}
