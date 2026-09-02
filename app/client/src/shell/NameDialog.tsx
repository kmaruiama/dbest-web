import { useState, type ReactNode } from "react";
import { useTranslation } from "../i18n";
import type { Board, Sessions } from "./useSessions";

type Props = {
  taken: string[];
  onCancel: () => void;
  onConfirm: (name: string) => void;
};

export function useNewSessionDialog(
  sessions: Sessions,
  board: Board,
): {
  start: () => void;
  dialog: ReactNode;
} {
  const [naming, setNaming] = useState(false);
  return {
    start: () => setNaming(true),
    dialog: naming ? (
      <NameDialog
        taken={board.files.map((file) => file.name)}
        onCancel={() => setNaming(false)}
        onConfirm={(name) => {
          setNaming(false);
          sessions.create(name);
        }}
      />
    ) : null,
  };
}

function NameDialog({ taken, onCancel, onConfirm }: Props) {
  const translate = useTranslation();
  const [draft, setDraft] = useState("");
  const name = draft.trim();
  const handleConfirm = () => {
    if (name.length > 0) onConfirm(name);
  };
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") handleConfirm();
    if (event.key === "Escape") onCancel();
  };
  return (
    <div className="overlay" onMouseDown={onCancel}>
      <div className="dialog" onMouseDown={(event) => event.stopPropagation()}>
        <h2>{translate("nameSession")}</h2>
        <input
          autoFocus
          data-testid="session-name-input"
          value={draft}
          placeholder={translate("namePlaceholder")}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        {taken.includes(name) && (
          <p className="detail">{translate("nameTaken")}</p>
        )}
        <div className="dialog-actions">
          <button type="button" className="btn-ghost" onClick={onCancel}>
            {translate("cancel")}
          </button>
          <button
            type="button"
            className="btn"
            data-testid="session-create"
            disabled={name.length === 0}
            onClick={handleConfirm}
          >
            {translate("create")}
          </button>
        </div>
      </div>
    </div>
  );
}
