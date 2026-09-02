import { useState } from "react";
import { useTranslation } from "../i18n";
import { useNewSessionDialog } from "./NameDialog";
import type { Board, Sessions } from "./useSessions";

type Props = {
  sessions: Sessions;
  board: Board;
};

export function TabBar({ sessions, board }: Props) {
  const translate = useTranslation();
  const [renaming, setRenaming] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const { start, dialog } = useNewSessionDialog(sessions, board);
  const ready = board.config.sessionsDir != null;
  const handleRename = (sid: string) => {
    const name = draft.trim();
    if (name.length > 0) sessions.rename(sid, name);
    setRenaming(null);
  };
  const startRename = (sid: string, name: string) => {
    setDraft(name);
    setRenaming(sid);
  };
  return (
    <div className="tabbar">
      {board.tabs.map((tab) => (
        <span
          key={tab.sid}
          className={tab.sid === board.active ? "tab active" : "tab"}
          onClick={() => sessions.select(tab.sid)}
        >
          {renaming === tab.sid ? (
            <input
              autoFocus
              className="tab-rename"
              value={draft}
              onClick={(event) => event.stopPropagation()}
              onChange={(event) => setDraft(event.target.value)}
              onBlur={() => handleRename(tab.sid)}
              onKeyDown={(event) => {
                if (event.key === "Enter") handleRename(tab.sid);
                if (event.key === "Escape") setRenaming(null);
              }}
            />
          ) : (
            <span
              className="tab-name"
              onDoubleClick={(event) => {
                event.stopPropagation();
                startRename(tab.sid, tab.name);
              }}
            >
              {tab.name.length > 0 ? tab.name : translate("untitled")}
            </span>
          )}
          {tab.dirty && <b className="dot">•</b>}
          <button
            type="button"
            className="row-remove"
            onClick={(event) => {
              event.stopPropagation();
              sessions.close(tab.sid);
            }}
          >
            ×
          </button>
        </span>
      ))}

      <button
        type="button"
        className="tab-new"
        disabled={!ready}
        title={translate(ready ? "newCanvas" : "needsFolder")}
        onClick={start}
      >
        +
      </button>

      {dialog}
    </div>
  );
}
