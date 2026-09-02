import { useTranslation } from "../i18n";
import { TabBar } from "./TabBar";
import type { Board, Sessions } from "./useSessions";

type Props = {
  sessions: Sessions;
  board: Board;
  onExit: () => void;
  onMainMenu: () => void;
};

export function Header({ sessions, board, onExit, onMainMenu }: Props) {
  const translate = useTranslation();
  const handleExit = () => {
    if (window.confirm(translate("confirmExit"))) onExit();
  };
  return (
    <header>
      <span className="wordmark">DBest</span>
      {board.active !== null && (
        <button type="button" className="btn-ghost" onClick={onMainMenu}>
          {translate("mainMenu")}
        </button>
      )}
      <TabBar sessions={sessions} board={board} />
      <div className="spacer" />
      <button type="button" className="btn-ghost" onClick={handleExit}>
        {translate("exit")}
      </button>
    </header>
  );
}
