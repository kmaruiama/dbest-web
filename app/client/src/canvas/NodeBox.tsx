import { Handle, Position, useStore, type NodeProps } from "@xyflow/react";
import { useTranslation } from "../i18n";
import type { Port } from "../server/types";
import type { BoxNode } from "./projection";

export type BoxData = {
  symbol: string;
  engineClass: string | null;
  expression: string;
  ports: Port[];
  unknownVariant: boolean;
  problem: string | null;
  dimmed: boolean;
  editable: boolean;
  onStartLink: () => void;
  onEdit: () => void;
  onRun: () => void;
  onDelete: () => void;
};

export function NodeBox({ data, selected }: NodeProps<BoxNode>) {
  const box = data;
  const translate = useTranslation();
  const solo = useStore(
    (state) =>
      state.nodes.reduce(
        (count, node) => count + (node.selected ? 1 : 0),
        0,
      ) === 1,
  );
  const classes = ["node-box"];
  if (box.ports.length === 0) classes.push("source");
  if (box.ports.length > 1) classes.push("binary");
  if (box.problem !== null) classes.push("has-problem");
  if (selected) classes.push("selected");
  if (box.dimmed) classes.push("dimmed");
  return (
    <div className={classes.join(" ")} title={box.problem ?? undefined}>
      {box.ports.length > 0 && (
        <Handle
          id="in"
          type="target"
          position={Position.Top}
          className="port"
          isConnectable={false}
        />
      )}

      {selected && (
        <div
          className="node-tools"
          data-testid="node-tools"
          onClick={(event) => event.stopPropagation()}
        >
          {solo && box.editable && (
            <button type="button" data-testid="node-edit" onClick={box.onEdit}>
              {translate("edit")}
            </button>
          )}
          {solo && (
            <button
              type="button"
              data-testid="node-link"
              onClick={box.onStartLink}
            >
              {translate("link")}
            </button>
          )}
          {solo && (
            <button
              type="button"
              className="run"
              data-testid="node-run"
              onClick={box.onRun}
            >
              {translate("run")}
            </button>
          )}
          <button
            type="button"
            className="danger"
            data-testid="node-delete"
            onClick={box.onDelete}
          >
            {translate("delete")}
          </button>
        </div>
      )}

      <div
        className={box.unknownVariant ? "node-symbol unknown" : "node-symbol"}
      >
        {box.symbol}
      </div>
      {box.engineClass !== null && (
        <div className="node-class">{box.engineClass}</div>
      )}
      {box.expression.length > 0 && (
        <div className="node-caption">{box.expression}</div>
      )}
      <Handle
        id="out"
        type="source"
        position={Position.Bottom}
        className="port"
        isConnectable={false}
      />
    </div>
  );
}
