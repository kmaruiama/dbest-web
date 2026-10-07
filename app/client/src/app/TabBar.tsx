import { useState } from "react";
import { useTranslation } from "../i18n";
import type { Dispatch } from "../ui/useStep";
import type { Board, Event } from "./app";

type Props = {
  board: Board;
  dispatch: Dispatch<Event>;
};

export function TabBar({ board, dispatch }: Props) {
  const translate = useTranslation();
  const [renaming, setRenaming] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const ready = board.config.sessionsDir != null;
  const handleRename = (sid: string) => {
    const name = draft.trim();
    if (name.length > 0) dispatch({ kind: "tabRenamed", sid, name });
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
          onClick={() => dispatch({ kind: "tabClicked", sid: tab.sid })}
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
          <button
            type="button"
            className="row-remove"
            onClick={(event) => {
              event.stopPropagation();
              dispatch({ kind: "tabCloseClicked", sid: tab.sid });
            }}
          >
            ×
          </button>
        </span>
      ))}

      <button
        type="button"
        className="btn-ghost compact"
        disabled={!ready}
        title={translate(ready ? "newCanvas" : "needsFolder")}
        onClick={() => dispatch({ kind: "newSessionClicked" })}
      >
        +
      </button>
    </div>
  );
}
