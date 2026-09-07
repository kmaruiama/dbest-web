import { useState } from "react";
import { useTranslation } from "../i18n";

type Props = {
  initial: string;
  onCancel: () => void;
  onConfirm: (alias: string) => void;
};

export function AliasDialog({ initial, onCancel, onConfirm }: Props) {
  const translate = useTranslation();
  const [draft, setDraft] = useState(initial);
  const renaming = initial.length > 0;
  const alias = draft.trim();
  const handleConfirm = () => {
    if (alias.length > 0) onConfirm(alias);
  };
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") handleConfirm();
    if (event.key === "Escape") onCancel();
  };
  return (
    <div className="overlay" onMouseDown={onCancel}>
      <div className="dialog" onMouseDown={(event) => event.stopPropagation()}>
        <h2>{translate(renaming ? "renameAlias" : "nameAlias")}</h2>
        <input
          autoFocus
          data-testid="alias-input"
          value={draft}
          placeholder={translate("aliasPlaceholder")}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <div className="dialog-actions">
          <button type="button" className="btn-ghost" onClick={onCancel}>
            {translate("cancel")}
          </button>
          <button
            type="button"
            className="btn"
            data-testid="alias-confirm"
            disabled={alias.length === 0}
            onClick={handleConfirm}
          >
            {translate(renaming ? "save" : "create")}
          </button>
        </div>
      </div>
    </div>
  );
}
