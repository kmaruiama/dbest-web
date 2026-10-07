import { useState } from "react";
import { useTranslation } from "../i18n";
import { Dialog } from "../ui/Dialog";

type Props = {
  taken: string[];
  onCancel: () => void;
  onConfirm: (name: string) => void;
};

export function NameDialog({ taken, onCancel, onConfirm }: Props) {
  const translate = useTranslation();
  const [draft, setDraft] = useState("");
  const name = draft.trim();
  const handleConfirm = () => {
    if (name.length > 0) onConfirm(name);
  };
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") handleConfirm();
  };
  return (
    <Dialog onClose={onCancel}>
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
    </Dialog>
  );
}
