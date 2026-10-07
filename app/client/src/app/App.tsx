import { ReactFlowProvider } from "@xyflow/react";
import { useCallback } from "react";
import { useTranslation } from "../i18n";
import { connect, messageOf, type Api } from "../server/api";
import type { FileEntry } from "../server/types";
import { ConnectionProvider, useApi } from "../ui/connection";
import { useSettings } from "../ui/settings";
import { ErrorBar, Failure, Loading } from "../ui/Status";
import { useLoad } from "../ui/useLoad";
import { useStep, type Dispatch } from "../ui/useStep";
import { Workspace } from "../workspace/Workspace";
import { initial, step, type Effect, type Event } from "./app";
import { Header } from "./Header";
import { Launcher } from "./Launcher";
import { NameDialog } from "./NameDialog";
import { SettingsDialog } from "./SettingsDialog";

const NO_FILES: FileEntry[] = [];

export function App() {
  const boot = useCallback(async () => {
    const api = await connect();
    const catalog = await api.operators();
    return { api, catalog };
  }, []);
  const { state, reload } = useLoad(boot);
  if (state.kind === "loading") return <Loading />;
  if (state.kind === "failed")
    return <Failure message={state.message} onRetry={reload} />;
  return (
    <ConnectionProvider api={state.value.api} catalog={state.value.catalog}>
      <Shell />
    </ConnectionProvider>
  );
}

function Shell() {
  const translate = useTranslation();
  const api = useApi();
  const settings = useSettings();
  const run = (effect: Effect, dispatch: Dispatch<Event>) =>
    perform(api, effect, dispatch);
  const [state, dispatch] = useStep(step, initial, run);
  if (state.exited)
    return <div className="centred faded">{translate("exited")}</div>;
  if (state.board.kind === "loading") return <Loading />;
  if (state.board.kind === "failed") {
    return (
      <Failure
        message={state.board.message}
        onRetry={() => dispatch({ kind: "retryClicked" })}
      />
    );
  }
  const board = state.board.value;
  return (
    <div className="app">
      <Header board={board} dispatch={dispatch} />
      {board.active === null ? (
        <Launcher board={board} dispatch={dispatch} />
      ) : (
        <ReactFlowProvider key={board.active}>
          <Workspace sid={board.active} />
        </ReactFlowProvider>
      )}
      {state.naming && (
        <NameDialog
          taken={board.files.map((file) => file.name)}
          onCancel={() => dispatch({ kind: "namingCancelled" })}
          onConfirm={(name) => dispatch({ kind: "nameChosen", name })}
        />
      )}
      {settings.dialogOpen && <SettingsDialog onClose={settings.closeDialog} />}
      {state.error !== null && (
        <ErrorBar
          message={state.error}
          onDismiss={() => dispatch({ kind: "errorDismissed" })}
        />
      )}
    </div>
  );
}

function perform(api: Api, effect: Effect, dispatch: Dispatch<Event>): void {
  const failed = (caught: unknown) =>
    dispatch({ kind: "failed", message: messageOf(caught) });
  switch (effect.kind) {
    case "loadBoard":
      void Promise.all([
        api.config(),
        api.listSessions(),
        api.listFiles().catch(() => NO_FILES),
      ]).then(
        ([config, tabs, files]) =>
          dispatch({
            kind: "boardLoaded",
            board: { config, files, tabs, active: tabs.at(-1)?.sid ?? null },
          }),
        (caught: unknown) =>
          dispatch({ kind: "boardFailed", message: messageOf(caught) }),
      );
      return;
    case "create":
      void api
        .newSession(effect.name)
        .then((meta) => dispatch({ kind: "sessionCreated", meta }), failed);
      return;
    case "open":
      void api
        .openSession(effect.path)
        .then((meta) => dispatch({ kind: "sessionOpened", meta }), failed);
      return;
    case "rename":
      void api
        .renameSession(effect.sid, effect.name)
        .then((meta) => dispatch({ kind: "sessionRenamed", meta }), failed);
      return;
    case "close":
      void api
        .closeSession(effect.sid)
        .then(
          () => dispatch({ kind: "sessionClosed", sid: effect.sid }),
          failed,
        );
      return;
    case "chooseFolder":
      void api
        .chooseDir()
        .then((config) => dispatch({ kind: "folderChosen", config }), failed);
      return;
    case "listFiles":
      void api.listFiles().then(
        (files) => dispatch({ kind: "filesListed", files }),
        () => dispatch({ kind: "filesListed", files: NO_FILES }),
      );
      return;
    case "shutdown":
      void api.shutdown();
      return;
  }
}
