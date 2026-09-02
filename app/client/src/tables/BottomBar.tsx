import { useTranslation } from "../i18n";
import { useSettingsDialog } from "../shell/SettingsModal";

type Props = {
  onNewTable: () => void;
  onImportTable: () => void;
};

export function BottomBar({ onNewTable, onImportTable }: Props) {
  const translate = useTranslation();
  const settings = useSettingsDialog();
  return (
    <footer className="bottombar">
      <button
        type="button"
        className="bottombar-btn"
        data-testid="bottombar-new-table"
        onClick={onNewTable}
      >
        {translate("newTable")}
      </button>
      <button
        type="button"
        className="bottombar-btn"
        data-testid="bottombar-import-table"
        onClick={onImportTable}
      >
        {translate("importTable")}
      </button>
      <div className="spacer" />
      {settings.gear}
      {settings.dialog}
    </footer>
  );
}
