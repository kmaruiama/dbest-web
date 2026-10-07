import { useState } from "react";
import { useTranslation } from "../i18n";
import { Dialog } from "../ui/Dialog";

type Props = {
  initial: string;
  error: string | null;
  busy: boolean;
  onCancel: () => void;
  onConfirm: (alias: string) => void;
};

export function AliasDialog({
  initial,
  error,
  busy,
  onCancel,
  onConfirm,
}: Props) {
  const translate = useTranslation();
  const [draft, setDraft] = useState(initial);
  const renaming = initial.length > 0;
  const alias = draft.trim();
  const dotted = alias.includes(".");
  const valid = alias.length > 0 && !dotted;
  const handleConfirm = () => {
    if (valid) onConfirm(alias);
  };
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") handleConfirm();
  };
  return (
    <Dialog onClose={onCancel}>
      <h2>{translate(renaming ? "renameAlias" : "nameAlias")}</h2>
      <input
        autoFocus
        data-testid="alias-input"
        value={draft}
        placeholder={translate("aliasPlaceholder")}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      {dotted && (
        <div className="form-error" data-testid="alias-error">
          {translate("aliasDot")}
        </div>
      )}
      {error !== null && !dotted && (
        <div className="form-error" data-testid="alias-refused">
          {error}
        </div>
      )}
      <div className="dialog-actions">
        <button type="button" className="btn-ghost" onClick={onCancel}>
          {translate("cancel")}
        </button>
        <button
          type="button"
          className="btn"
          data-testid="alias-confirm"
          disabled={!valid || busy}
          onClick={handleConfirm}
        >
          {translate(renaming ? "save" : "create")}
        </button>
      </div>
    </Dialog>
  );
}
