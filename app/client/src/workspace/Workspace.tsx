import { useEffect } from "react";
import { Canvas } from "../canvas/Canvas";
import { LinkChoice } from "../canvas/LinkChoice";
import { Palette } from "../canvas/Palette";
import { Form } from "../form/Form";
import { useTranslation } from "../i18n";
import { Comparator } from "../results/Comparator";
import { Results } from "../results/Results";
import { messageOf, type Api } from "../server/api";
import type { NodeId, SessionView } from "../server/types";
import { TableModal } from "../tables/TableModal";
import { useApi, useCatalog } from "../ui/connection";
import { useSettings } from "../ui/settings";
import { ErrorBar, Failure, Loading } from "../ui/Status";
import { useStep, type Dispatch } from "../ui/useStep";
import { AliasDialog } from "./AliasDialog";
import { BottomBar } from "./BottomBar";
import { Toolbar } from "./Toolbar";
import {
  busy,
  initial,
  liveMarks,
  marksApart,
  step,
  titleOf,
  type Effect,
  type Event,
  type Loaded,
  type Notice,
  type State,
  type Write,
} from "./workspace";

type Props = {
  sid: string;
};

export function Workspace({ sid }: Props) {
  const translate = useTranslation();
  const api = useApi();
  const catalog = useCatalog();
  const settings = useSettings();
  const run = (effect: Effect, dispatch: Dispatch<Event>) =>
    perform(api, sid, effect, dispatch);
  const [state, dispatch] = useStep(step, initial(sid, catalog), run);
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      const pressed = keyEvent(event);
      if (pressed === null) return;
      if (pressed.kind === "deletePressed") event.preventDefault();
      dispatch(pressed);
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [dispatch]);
  if (state.load.kind === "loading") return <Loading />;
  if (state.load.kind === "failed") {
    return (
      <Failure
        message={state.load.message}
        onRetry={() => dispatch({ kind: "retryClicked" })}
      />
    );
  }
  const { view, problems } = state.load.value;
  const marks = liveMarks(state);
  const linkingFrom =
    state.interaction.kind === "linking" ? state.interaction.from : null;
  return (
    <div className="workspace">
      <Toolbar
        view={view}
        marks={marks}
        marksApart={marksApart(state)}
        dispatch={dispatch}
      />
      <div className="body">
        <main>
          {Object.keys(view.session.nodes).length === 0 && (
            <div className="centred faded">{translate("emptyCanvas")}</div>
          )}
          {linkingFrom !== null && (
            <div className="link-hint">{translate("linkHint")}</div>
          )}
          <Canvas
            session={view.session}
            look={{
              captions: view.captions,
              problems,
              selection: state.selection,
              edgeSelection: state.edgeSelection,
              linkingFrom,
              marks,
              drag: state.drag,
              sizes: state.sizes,
              showEngineClass: settings.showEngineClass,
              showExpression: settings.showExpression,
              translate,
            }}
            onEvent={dispatch}
          />
        </main>
        <Palette session={view.session} onEvent={dispatch} />
      </div>
      <BottomBar
        onNewTable={() => dispatch({ kind: "newTableClicked", mode: "create" })}
        onImportTable={() =>
          dispatch({ kind: "newTableClicked", mode: "import" })
        }
      />
      <ActiveModal state={state} dispatch={dispatch} />
      {state.notice !== null && (
        <ErrorBar
          message={noticeText(state.notice, translate)}
          onDismiss={() => dispatch({ kind: "noticeDismissed" })}
        />
      )}
    </div>
  );
}

type ActiveModalProps = {
  state: State;
  dispatch: Dispatch<Event>;
};

function ActiveModal({ state, dispatch }: ActiveModalProps) {
  const interaction = state.interaction;
  const close = () => dispatch({ kind: "modalClosed" });
  switch (interaction.kind) {
    case "editing":
      return (
        <Form
          key={interaction.id}
          node={interaction.node}
          columns={interaction.columns}
          error={interaction.error}
          busy={busy(state)}
          onSubmit={(node) =>
            dispatch({ kind: "formSubmitted", id: interaction.id, node })
          }
          onCancel={close}
        />
      );
    case "renamingAlias":
      return (
        <AliasDialog
          initial={interaction.current}
          error={interaction.error}
          busy={busy(state)}
          onCancel={close}
          onConfirm={(alias) =>
            dispatch({ kind: "aliasConfirmed", id: interaction.id, alias })
          }
        />
      );
    case "newTable":
      return (
        <TableModal
          mode={interaction.mode}
          onAdd={(spec) => dispatch({ kind: "tableSubmitted", spec })}
          onClose={close}
        />
      );
    case "running":
      return <Results sid={state.sid} id={interaction.id} onClose={close} />;
    case "comparing":
      return (
        <Comparator
          sid={state.sid}
          sides={[
            sideOf(state, interaction.a, "A"),
            sideOf(state, interaction.b, "B"),
          ]}
          onClose={close}
        />
      );
    case "choosing":
      return (
        <LinkChoice
          options={interaction.options}
          onChoose={(edge) => dispatch({ kind: "linkChosen", edge })}
          onCancel={close}
        />
      );
    case "idle":
    case "linking":
      return null;
  }
}

function sideOf(state: State, id: NodeId, letter: string) {
  return { id, letter, title: titleOf(state, id) };
}

function keyEvent(event: KeyboardEvent): Event | null {
  if (event.key === "Escape") return { kind: "escapePressed" };
  if (document.activeElement?.tagName === "INPUT") return null;
  if (event.key === "Delete" || event.key === "Backspace")
    return { kind: "deletePressed" };
  const shortcut = event.ctrlKey || event.metaKey;
  if (shortcut && event.key === "c") return { kind: "copyPressed" };
  if (shortcut && event.key === "v") return { kind: "pastePressed" };
  return null;
}

function noticeText(
  notice: Notice,
  translate: (key: string) => string,
): string {
  return "key" in notice ? translate(notice.key) : notice.message;
}

function perform(
  api: Api,
  sid: string,
  effect: Effect,
  dispatch: Dispatch<Event>,
): void {
  switch (effect.kind) {
    case "load":
      void withProblems(api, sid, api.session(sid)).then(
        ({ view, problems }) => dispatch({ kind: "loaded", view, problems }),
        (caught: unknown) =>
          dispatch({ kind: "loadFailed", message: messageOf(caught) }),
      );
      return;
    case "send":
      void withProblems(api, sid, written(api, sid, effect.write)).then(
        (loaded) =>
          dispatch({
            kind: "committed",
            seq: effect.seq,
            loaded,
            done: effect.done,
          }),
        (caught: unknown) =>
          dispatch({ kind: "commandFailed", message: messageOf(caught) }),
      );
      return;
    case "loadColumns":
      void api.columns(sid, effect.id).then(
        (columns) =>
          dispatch({
            kind: "columnsLoaded",
            id: effect.id,
            node: effect.node,
            columns,
          }),
        (caught: unknown) =>
          dispatch({ kind: "columnsFailed", message: messageOf(caught) }),
      );
      return;
  }
}

async function withProblems(
  api: Api,
  sid: string,
  answer: Promise<SessionView>,
): Promise<Loaded> {
  const view = await answer;
  const problems = await api.problems(sid);
  return { view, problems };
}

function written(api: Api, sid: string, write: Write): Promise<SessionView> {
  switch (write.kind) {
    case "command":
      return api.commands(sid, write.command);
    case "undo":
      return api.undo(sid);
    case "redo":
      return api.redo(sid);
    case "rename":
      return api.rename(sid, write.id, write.alias);
  }
}
