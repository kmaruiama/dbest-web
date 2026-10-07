import { useTranslation } from "../i18n";
import type { Dispatch } from "../ui/useStep";
import type { Board, Event } from "./app";
import { TabBar } from "./TabBar";

type Props = {
  board: Board;
  dispatch: Dispatch<Event>;
};

export function Header({ board, dispatch }: Props) {
  const translate = useTranslation();
  const handleExit = () => {
    if (window.confirm(translate("confirmExit")))
      dispatch({ kind: "exitConfirmed" });
  };
  return (
    <header>
      <span className="wordmark">DBest</span>
      {board.active !== null && (
        <button
          type="button"
          className="btn-ghost"
          onClick={() => dispatch({ kind: "mainMenuClicked" })}
        >
          {translate("mainMenu")}
        </button>
      )}
      <TabBar board={board} dispatch={dispatch} />
      <div className="spacer" />
      <button type="button" className="btn-ghost" onClick={handleExit}>
        {translate("exit")}
      </button>
    </header>
  );
}
