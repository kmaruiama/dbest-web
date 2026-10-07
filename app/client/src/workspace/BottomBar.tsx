import { useTranslation } from "../i18n";
import { useSettings } from "../ui/settings";

type Props = {
  onNewTable: () => void;
  onImportTable: () => void;
};

export function BottomBar({ onNewTable, onImportTable }: Props) {
  const translate = useTranslation();
  const settings = useSettings();
  return (
    <footer className="bottombar">
      <button
        type="button"
        className="btn-ghost"
        data-testid="bottombar-new-table"
        onClick={onNewTable}
      >
        {translate("newTable")}
      </button>
      <button
        type="button"
        className="btn-ghost"
        data-testid="bottombar-import-table"
        onClick={onImportTable}
      >
        {translate("importTable")}
      </button>
      <div className="spacer" />
      <button
        type="button"
        className="gear"
        title={translate("settings")}
        aria-label={translate("settings")}
        onClick={settings.openDialog}
      >
        ⚙
      </button>
    </footer>
  );
}
