import { useReactFlow } from "@xyflow/react";
import { useCallback, useEffect, useReducer, useRef } from "react";
import { Canvas } from "../canvas/Canvas";
import { LinkChoice } from "../canvas/LinkChoice";
import { Palette } from "../canvas/Palette";
import { Form } from "../form/Form";
import { useTranslation } from "../i18n";
import { Results } from "../results/Results";
import { messageOf } from "../server/api";
import { chipByKey, isEditable } from "../server/catalog";
import type {
  Command,
  Edge,
  NodeId,
  PlanNode,
  Position,
  Session,
  TableId,
  TableSpec,
} from "../server/types";
import { BottomBar } from "../tables/BottomBar";
import { TableModal } from "../tables/TableModal";
import { ErrorBar, Failure, Loading } from "../ui/Status";
import { AliasDialog } from "./AliasDialog";
import { columnsFeeding } from "./columns";
import { hasAllInputPortsConnected, resolveLink } from "./graph";
import { mint } from "./ids";
import { INITIAL, reduce } from "./interaction";
import { useSession } from "./useSession";

const PASTE_OFFSET = 30;

type Props = {
  sid: string;
  onMutate: () => void;
  onSave: () => void;
};

export function Workspace({ sid, onMutate, onSave }: Props) {
  const translate = useTranslation();
  const [state, dispatch] = useReducer(reduce, INITIAL);
  const nodeWatermark = useRef(0);
  const tableWatermark = useRef(0);
  const clipboard = useRef<{
    entries: {
      originalId: NodeId;
      node: PlanNode;
      at: Position;
    }[];
    edges: Edge[];
  } | null>(null);
  const pendingPasteSelection = useRef<Set<NodeId> | null>(null);
  const flow = useReactFlow();
  const store = useSession(sid, onMutate);
  const { interaction, notice } = state;
  const { session, sendCommand } = store;
  const message =
    interaction.kind === "editing" ? notice : (notice ?? store.error);
  const selectedIds = useCallback(
    (): NodeId[] =>
      flow
        .getNodes()
        .filter((node) => node.selected)
        .map((node) => Number(node.id)),
    [flow],
  );
  const setSelection = useCallback(
    (ids: Iterable<NodeId>) => {
      const want = new Set<string>();
      for (const id of ids) want.add(String(id));
      flow.setNodes((nodes) =>
        nodes.map((node) =>
          node.selected === want.has(node.id)
            ? node
            : { ...node, selected: want.has(node.id) },
        ),
      );
    },
    [flow],
  );
  const stop = useCallback(() => dispatch({ kind: "reset" }), []);
  const deselect = useCallback(() => {
    setSelection([]);
    stop();
  }, [setSelection, stop]);
  const connect = (edge: Edge) => sendCommand({ kind: "connect", edge });
  const openEditWith = async (id: NodeId, latest: Session) => {
    const node = latest.nodes.get(id);
    if (node === undefined || !isEditable(node.kind)) return;
    try {
      const columns = await columnsFeeding(sid, latest, id);
      dispatch({ kind: "edit", id, node, columns });
    } catch (caught) {
      dispatch({ kind: "notify", message: messageOf(caught) });
    }
  };
  const openEdit = (id: NodeId) => openEditWith(id, session);
  const connectAndMaybeEdit = async (edge: Edge) => {
    const result = await connect(edge);
    if (result.kind !== "success") return;
    const latest = result.view.session;
    if (hasAllInputPortsConnected(latest, edge.to))
      void openEditWith(edge.to, latest);
  };
  const completeLink = (from: NodeId, to: NodeId) => {
    const resolution = resolveLink(session, from, to);
    if (resolution.kind === "none") {
      dispatch({ kind: "notify", message: translate(resolution.reason) });
      return;
    }
    if (resolution.kind === "one") {
      void connectAndMaybeEdit(resolution.link);
      stop();
      return;
    }
    dispatch({ kind: "choose", options: resolution.options });
  };
  const handleStartLink = (id: NodeId) => {
    setSelection([]);
    dispatch({ kind: "startLink", from: id });
  };
  const handleNodeClick = (id: NodeId) => {
    if (interaction.kind === "linking") {
      completeLink(interaction.from, id);
      setSelection([]);
      return;
    }
    handleStartLink(id);
  };
  const handleNodeContextMenu = (id: NodeId, additive: boolean) => {
    if (interaction.kind === "linking") {
      completeLink(interaction.from, id);
      setSelection([]);
      return;
    }
    if (additive) {
      const next = new Set(selectedIds());
      if (next.has(id)) next.delete(id);
      else next.add(id);
      setSelection(next);
    } else {
      setSelection([id]);
    }
    dispatch({ kind: "clearNotice" });
  };
  const handlePaneContextMenu = () => {
    if (interaction.kind === "linking") {
      stop();
      return;
    }
    deselect();
  };
  const handleSelectionChange = useCallback(
    () => dispatch({ kind: "clearNotice" }),
    [],
  );
  const handleDeleteSelected = useCallback(async () => {
    for (const id of selectedIds())
      await sendCommand({ kind: "removeNode", id });
    stop();
  }, [selectedIds, sendCommand, stop]);
  const handleMove = (moves: Map<NodeId, Position>) => {
    const commands: Command[] = [];
    for (const [id, to] of moves) {
      const at = session.layout.get(id);
      if (at !== undefined && at.x === to.x && at.y === to.y) continue;
      commands.push({ kind: "move", id, to });
    }
    if (commands.length === 0) return;
    void sendCommand({ kind: "batch", commands });
  };
  const handleDropChip = (key: string, at: Position) => {
    const chip = chipByKey(key);
    if (chip === undefined) return;
    sendCommand({
      kind: "addNode",
      id: mint(session.nodes.keys(), nodeWatermark),
      node: chip.template,
      at,
    });
  };
  const addScan = (table: TableId, at: Position) =>
    dispatch({ kind: "nameAlias", table, at });
  const copySelection = useCallback(() => {
    const ids = new Set(selectedIds());
    if (ids.size === 0) return;
    const entries: {
      originalId: NodeId;
      node: PlanNode;
      at: Position;
    }[] = [];
    for (const id of ids) {
      const node = session.nodes.get(id);
      const at = session.layout.get(id);
      if (node === undefined || at === undefined) continue;
      entries.push({ originalId: id, node: structuredClone(node), at });
    }
    const edges = session.edges.filter(
      (edge) => ids.has(edge.from) && ids.has(edge.to),
    );
    clipboard.current = { entries, edges };
  }, [session, selectedIds]);
  const pasteClipboard = useCallback(async () => {
    const clip = clipboard.current;
    if (clip === null || clip.entries.length === 0) return;
    const remap = new Map<NodeId, NodeId>();
    const commands: Command[] = [];
    for (const entry of clip.entries) {
      remap.set(entry.originalId, mint(session.nodes.keys(), nodeWatermark));
    }
    for (const entry of clip.entries) {
      commands.push({
        kind: "addNode",
        id: remap.get(entry.originalId) as NodeId,
        node: entry.node,
        at: {
          x: entry.at.x + PASTE_OFFSET,
          y: entry.at.y + PASTE_OFFSET,
        },
      });
    }
    for (const edge of clip.edges) {
      const from = remap.get(edge.from);
      const to = remap.get(edge.to);
      if (from !== undefined && to !== undefined) {
        commands.push({
          kind: "connect",
          edge: { from, to, port: edge.port },
        });
      }
    }
    const pasted = new Set(remap.values());
    pendingPasteSelection.current = pasted;
    const result = await sendCommand({ kind: "batch", commands });
    if (result.kind !== "success" && pendingPasteSelection.current === pasted)
      pendingPasteSelection.current = null;
  }, [session, sendCommand]);
  const submitEdit = async (id: NodeId, node: PlanNode) => {
    const result = await sendCommand({ kind: "setNode", id, node });
    if (result.kind === "success") stop();
  };
  const confirmScan = (table: TableId, at: Position, alias: string) => {
    sendCommand({
      kind: "addNode",
      id: mint(session.nodes.keys(), nodeWatermark),
      node: { kind: "table", fields: { table, alias } },
      at,
    });
    stop();
  };
  const addTable = async (spec: TableSpec) => {
    const id = mint(session.tables.keys(), tableWatermark);
    const result = await sendCommand({ kind: "addTable", id, spec });
    if (result.kind === "success") stop();
  };
  useEffect(() => {
    const pasted = pendingPasteSelection.current;
    if (pasted === null || ![...pasted].every((id) => session.nodes.has(id)))
      return;
    pendingPasteSelection.current = null;
    setSelection(pasted);
  }, [session, setSelection]);
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        deselect();
        return;
      }
      const typing = document.activeElement?.tagName === "INPUT";
      if (typing) return;
      if (
        (event.key === "Delete" || event.key === "Backspace") &&
        selectedIds().length > 0
      ) {
        event.preventDefault();
        void handleDeleteSelected();
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key === "c") {
        copySelection();
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key === "v") {
        void pasteClipboard();
      }
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [
    selectedIds,
    handleDeleteSelected,
    deselect,
    copySelection,
    pasteClipboard,
  ]);
  if (store.load.kind === "loading") return <Loading />;
  if (store.load.kind === "failed") {
    return <Failure message={store.load.message} onRetry={store.refresh} />;
  }
  return (
    <div className="workspace">
      <div className="workspace-toolbar">
        <button
          type="button"
          className="toolbtn"
          disabled={!store.load.view.canUndo}
          onClick={store.undo}
        >
          ↶ {translate("undo")}
        </button>
        <button
          type="button"
          className="toolbtn"
          disabled={!store.load.view.canRedo}
          onClick={store.redo}
        >
          ↷ {translate("redo")}
        </button>
        <span className="revision">
          {translate("stepsLabel", { n: store.load.view.depth })}
        </span>
        <div className="spacer" />
        <button type="button" className="toolbtn" onClick={onSave}>
          {translate("save")}
        </button>
      </div>

      <div className="body">
        <main>
          {session.nodes.size === 0 && (
            <div className="centred faded">{translate("emptyCanvas")}</div>
          )}
          {interaction.kind === "linking" && (
            <div className="link-hint">{translate("linkHint")}</div>
          )}
          <Canvas
            session={session}
            captions={store.captions}
            problems={store.problems}
            linkingFrom={
              interaction.kind === "linking" ? interaction.from : null
            }
            onNodeClick={handleNodeClick}
            onNodeContextMenu={handleNodeContextMenu}
            onPaneContextMenu={handlePaneContextMenu}
            onSelectionChange={handleSelectionChange}
            onStartLink={handleStartLink}
            onEdit={(id) => void openEdit(id)}
            onRun={(id) => dispatch({ kind: "run", id })}
            onDelete={handleDeleteSelected}
            onMove={handleMove}
            onDropChip={handleDropChip}
            onDropTable={addScan}
            onBlankClick={deselect}
          />
        </main>

        <Palette
          session={session}
          onAddScan={(id) => addScan(id, { x: 60, y: 60 })}
          onRemoveTable={(id) => void sendCommand({ kind: "removeTable", id })}
          onDeselect={deselect}
        />
      </div>

      <BottomBar
        onNewTable={() => dispatch({ kind: "newTable", mode: "create" })}
        onImportTable={() => dispatch({ kind: "newTable", mode: "import" })}
      />

      {interaction.kind === "editing" && (
        <Form
          key={interaction.id}
          node={interaction.node}
          columns={interaction.columns}
          error={store.error}
          busy={store.busy}
          onSubmit={(next) => void submitEdit(interaction.id, next)}
          onCancel={() => {
            store.dismissError();
            stop();
          }}
        />
      )}

      {interaction.kind === "namingAlias" && (
        <AliasDialog
          onCancel={stop}
          onConfirm={(alias) =>
            confirmScan(interaction.table, interaction.at, alias)
          }
        />
      )}

      {interaction.kind === "newTable" && (
        <TableModal
          mode={interaction.mode}
          onAdd={(spec) => void addTable(spec)}
          onClose={stop}
        />
      )}

      {interaction.kind === "running" && (
        <Results sid={sid} id={interaction.id} onClose={stop} />
      )}

      {interaction.kind === "choosing" && (
        <LinkChoice
          options={interaction.options}
          onChoose={(link) => {
            void connectAndMaybeEdit(link);
            stop();
          }}
          onCancel={stop}
        />
      )}

      {message !== null && (
        <ErrorBar
          message={message}
          onDismiss={() => {
            dispatch({ kind: "clearNotice" });
            store.dismissError();
          }}
        />
      )}
    </div>
  );
}
