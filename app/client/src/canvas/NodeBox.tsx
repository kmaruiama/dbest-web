import { Handle, Position, type NodeProps } from "@xyflow/react";
import { createContext, useContext } from "react";
import { useTranslation } from "../i18n";
import type { BoxNode, CanvasEvent, Tool } from "./canvas";

export const NodeEvents = createContext<(event: CanvasEvent) => void>(
  () => undefined,
);

export function NodeBox({ id, data: box, selected }: NodeProps<BoxNode>) {
  const translate = useTranslation();
  const send = useContext(NodeEvents);
  const handleTool = (tool: Tool) =>
    send({ kind: "toolClicked", tool, id: Number(id) });
  const classes = ["node-box"];
  if (box.ports.length === 0) classes.push("source");
  if (box.ports.length > 1) classes.push("binary");
  if (box.problem !== null) classes.push("has-problem");
  if (selected) classes.push("selected");
  if (box.dimmed) classes.push("dimmed");
  if (box.mark !== null) classes.push("marked");
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

      {box.mark !== null && <span className="node-mark">{box.mark}</span>}

      {selected && (
        <div
          className="node-tools"
          data-testid="node-tools"
          onClick={(event) => event.stopPropagation()}
        >
          {box.solo && box.editable && (
            <button
              type="button"
              data-testid="node-edit"
              onClick={() => handleTool("edit")}
            >
              {translate("edit")}
            </button>
          )}
          {box.solo && box.alias !== null && (
            <button
              type="button"
              data-testid="node-alias"
              onClick={() => handleTool("alias")}
            >
              {translate("alias")}
            </button>
          )}
          {box.solo && (
            <button
              type="button"
              data-testid="node-link"
              onClick={() => handleTool("link")}
            >
              {translate("link")}
            </button>
          )}
          {box.solo && (
            <button
              type="button"
              data-testid="node-mark"
              onClick={() => handleTool("mark")}
            >
              {translate(box.mark === null ? "mark" : "unmark")}
            </button>
          )}
          {box.solo && (
            <button
              type="button"
              className="run"
              data-testid="node-run"
              onClick={() => handleTool("run")}
            >
              {translate("run")}
            </button>
          )}
          <button
            type="button"
            className="danger"
            data-testid="node-delete"
            onClick={() => handleTool("delete")}
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
      {box.alias !== null && box.alias.length > 0 && (
        <div className="node-alias">as {box.alias}</div>
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
