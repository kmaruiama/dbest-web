import { ReactFlowProvider } from "@xyflow/react";
import { useCallback, useState } from "react";
import { useAsync } from "../hooks/useAsync";
import { useTranslation } from "../i18n";
import { api } from "../server/api";
import { installCatalog } from "../server/catalog";
import { ErrorBar, Failure, Loading } from "../ui/Status";
import { Workspace } from "../workspace/Workspace";
import { Header } from "./Header";
import { Launcher } from "./Launcher";
import { useSessions } from "./useSessions";

export function App() {
  const bootstrap = useCallback(async () => {
    await api.bootstrap();
    const operators = await api.operators();
    installCatalog(operators);
  }, []);

  const { state, reload } = useAsync(bootstrap, []);

  if (state.kind === "loading") return <Loading />;
  if (state.kind === "failed")
    return <Failure message={state.message} onRetry={reload} />;
  return <Shell />;
}

function Shell() {
  const translate = useTranslation();
  const [exited, setExited] = useState(false);
  const sessions = useSessions();
  const { markDirty, save } = sessions;
  const active =
    sessions.load.kind === "ready" ? sessions.load.value.active : null;
  const handleMutate = useCallback(() => {
    if (active !== null) markDirty(active);
  }, [active, markDirty]);
  const handleSave = () => {
    if (active !== null) save(active);
  };
  const handleExit = () => {
    void api.shutdown();
    setExited(true);
  };
  if (exited) return <div className="centred faded">{translate("exited")}</div>;
  if (sessions.load.kind === "loading") return <Loading />;
  if (sessions.load.kind === "failed") {
    return (
      <Failure message={sessions.load.message} onRetry={sessions.reload} />
    );
  }
  const board = sessions.load.value;
  return (
    <div className="app">
      <Header
        sessions={sessions}
        board={board}
        onExit={handleExit}
        onMainMenu={sessions.showLauncher}
      />
      {board.active === null ? (
        <Launcher sessions={sessions} board={board} />
      ) : (
        <ReactFlowProvider key={board.active}>
          <Workspace
            sid={board.active}
            onMutate={handleMutate}
            onSave={handleSave}
          />
        </ReactFlowProvider>
      )}
      {sessions.error !== null && (
        <ErrorBar message={sessions.error} onDismiss={sessions.dismissError} />
      )}
    </div>
  );
}
