import { RowsGrid } from "./RowsGrid";
import { ColumnsGrid } from "./ColumnsGrid";
import { SamplePreview } from "./SamplePreview";
import { CsvOptions } from "./CsvOptions";
import { XmlOptions } from "./XmlOptions";
import { useTranslation } from "../i18n";
import { messageOf, type Api } from "../server/api";
import type { TableSpec } from "../server/types";
import { useApi } from "../ui/connection";
import { Dialog } from "../ui/Dialog";
import { useStep, type Dispatch } from "../ui/useStep";
import {
  initial,
  pickedSpec,
  specOf,
  step,
  type Effect,
  type Event,
  type TableMode,
} from "./tables";

type Props = {
  mode: TableMode;
  onAdd: (spec: TableSpec) => void;
  onClose: () => void;
};

export function TableModal({ mode, onAdd, onClose }: Props) {
  const translate = useTranslation();
  const api = useApi();
  const run = (effect: Effect, dispatch: Dispatch<Event>) =>
    perform(api, effect, dispatch);
  const [state, dispatch] = useStep(step, initial(mode), run);
  const { tab, name, columns, rows, sampleRows, previewing, error } = state;
  const spec = pickedSpec(state);
  const showGrid =
    tab === "create" || (spec !== null && spec["@type"] !== "btree");
  const handleSubmit = () => {
    const built = specOf(state);
    if (built !== null) onAdd(built);
  };
  return (
    <Dialog className="form wide" testId="table-modal" onClose={onClose}>
      <div className="tabs">
        <button
          type="button"
          data-testid="table-modal-tab-create"
          className={tab === "create" ? "active" : ""}
          onClick={() => dispatch({ kind: "tabClicked", tab: "create" })}
        >
          {translate("newTable")}
        </button>
        <button
          type="button"
          data-testid="table-modal-tab-import"
          className={tab === "import" ? "active" : ""}
          onClick={() => dispatch({ kind: "tabClicked", tab: "import" })}
        >
          {translate("importTable")}
        </button>
      </div>

      {tab === "import" && (
        <div className="field">
          <button
            type="button"
            className="btn"
            data-testid="table-modal-choose-file"
            onClick={() => dispatch({ kind: "pickClicked" })}
          >
            {translate("chooseFile")}
          </button>
          {spec !== null && <span className="faded detail">{spec.path}</span>}
        </div>
      )}

      <div className="field">
        <label>{translate("tableName")}</label>
        <input
          type="text"
          data-testid="table-modal-name"
          value={name}
          onChange={(event) =>
            dispatch({ kind: "nameTyped", name: event.target.value })
          }
        />
      </div>

      {spec !== null && spec["@type"] === "xml" && (
        <XmlOptions spec={spec} previewing={previewing} dispatch={dispatch} />
      )}

      {spec !== null && spec["@type"] === "csv" && (
        <CsvOptions spec={spec} previewing={previewing} dispatch={dispatch} />
      )}

      {spec !== null &&
        (spec["@type"] === "csv" || spec["@type"] === "xml") &&
        sampleRows.length > 0 && (
          <SamplePreview columns={columns} sampleRows={sampleRows} />
        )}

      {showGrid && <ColumnsGrid columns={columns} dispatch={dispatch} />}

      {tab === "create" && (
        <RowsGrid columns={columns} rows={rows} dispatch={dispatch} />
      )}

      {error !== null && (
        <div className="form-error" data-testid="table-modal-error">
          {error}
        </div>
      )}

      <div className="dialog-actions">
        <button
          type="button"
          className="btn-ghost"
          data-testid="table-modal-cancel"
          onClick={onClose}
        >
          {translate("cancel")}
        </button>
        <button
          type="button"
          className="btn"
          data-testid="table-modal-save"
          onClick={handleSubmit}
        >
          {translate("save")}
        </button>
      </div>
    </Dialog>
  );
}

function perform(api: Api, effect: Effect, dispatch: Dispatch<Event>): void {
  const failed = (caught: unknown) =>
    dispatch({ kind: "previewFailed", message: messageOf(caught) });
  switch (effect.kind) {
    case "pick":
      void api.pickFile().then(
        (file) => dispatch({ kind: "picked", asked: effect.asked, file }),
        (caught: unknown) =>
          dispatch({
            kind: "pickFailed",
            asked: effect.asked,
            message: messageOf(caught),
          }),
      );
      return;
    case "previewCsv":
      void api
        .csvPreview(effect.path, effect.headerLine, effect.separator)
        .then((guess) => dispatch({ kind: "csvPreviewed", guess }), failed);
      return;
    case "previewXml":
      void api
        .xmlPreview(effect.path, effect.rootElement, effect.recordElement)
        .then((guess) => dispatch({ kind: "xmlPreviewed", guess }), failed);
      return;
  }
}
