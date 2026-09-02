import { useReducer } from "react";
import { useTranslation } from "../i18n";
import { api, messageOf } from "../server/api";
import {
  acceptsLiteral,
  columnLiteral,
  COLUMN_TYPES,
  type ColumnType,
} from "../server/literals";
import type { TableSpec } from "../server/types";
import { initialForm, reduceForm } from "./tableForm";

const SEPARATOR_OPTIONS: {
  value: string;
  label: string;
}[] = [
  { value: ",", label: "," },
  { value: ";", label: ";" },
  { value: "\t", label: "tab" },
  { value: "|", label: "|" },
];

export type TableMode = "create" | "import";

type Props = {
  mode: TableMode;
  onAdd: (spec: TableSpec) => void;
  onClose: () => void;
};

export function TableModal({ mode, onAdd, onClose }: Props) {
  const translate = useTranslation();
  const [state, dispatch] = useReducer(reduceForm, mode, initialForm);
  const { tab, name, columns, rows, picked, sampleRows, previewing, error } =
    state;
  const spec = picked.kind === "spec" ? picked.spec : null;
  const showGrid = tab === "create" || (spec !== null && spec.kind !== "btree");
  const handleChoose = async () => {
    dispatch({ kind: "pickStart" });
    try {
      const result = await api.pickFile();
      if (result.kind === "cancelled")
        return dispatch({ kind: "pickCancelled" });
      const { path } = result;
      if (result.kind === "csv") {
        return dispatch({
          kind: "pickChosen",
          name: result.name,
          spec: {
            kind: "csv",
            name: result.name,
            path,
            separator: result.separator,
            headerLine: result.headerLine,
            columns: [],
          },
          preview: result,
        });
      }
      if (result.kind === "xml") {
        return dispatch({
          kind: "pickChosen",
          name: result.name,
          spec: {
            kind: "xml",
            name: result.name,
            path,
            rootElement: result.rootElement,
            recordElement: result.recordElement,
            columns: [],
          },
          preview: result,
        });
      }
      dispatch({
        kind: "pickChosen",
        name: result.name,
        spec: { kind: "btree", name: result.name, path },
      });
    } catch (caught) {
      dispatch({ kind: "pickFailed", message: messageOf(caught) });
    }
  };
  const rePreviewCsv = async (patch: {
    separator?: string;
    headerLine?: number;
  }) => {
    if (spec === null || spec.kind !== "csv") return;
    const { path } = spec;
    const separator = patch.separator ?? spec.separator;
    const headerLine = patch.headerLine ?? spec.headerLine;
    dispatch({ kind: "previewStart" });
    try {
      const preview = await api.csvPreview(path, headerLine, separator);
      dispatch({
        kind: "previewApplied",
        preview,
        spec: {
          ...spec,
          separator: preview.separator,
          headerLine: preview.headerLine,
        },
      });
    } catch (caught) {
      dispatch({ kind: "previewFailed", message: messageOf(caught) });
    }
  };
  const rePreviewXml = async (patch: {
    rootElement?: string;
    recordElement?: string;
  }) => {
    if (spec === null || spec.kind !== "xml") return;
    const { path } = spec;
    const rootElement = patch.rootElement ?? spec.rootElement;
    const recordElement = patch.recordElement ?? spec.recordElement;
    dispatch({ kind: "previewStart" });
    try {
      const preview = await api.xmlPreview(path, rootElement, recordElement);
      dispatch({
        kind: "previewApplied",
        preview,
        spec: {
          ...spec,
          rootElement: preview.rootElement,
          recordElement: preview.recordElement,
        },
      });
    } catch (caught) {
      dispatch({ kind: "previewFailed", message: messageOf(caught) });
    }
  };
  const handleSubmit = () => {
    const shaped = columns
      .filter((column) => column.name.trim().length > 0)
      .map((column) => ({ ...column, name: column.name.trim() }));
    const base: TableSpec | null =
      tab === "create"
        ? {
            kind: "memory",
            name: name.trim(),
            columns: shaped,
            rows: rows.map((row) => {
              const record: Record<
                string,
                ReturnType<typeof columnLiteral>
              > = {};
              shaped.forEach((column, index) => {
                record[column.name] = columnLiteral(
                  column.type,
                  row[index] ?? "",
                );
              });
              return record;
            }),
          }
        : spec;
    if (base === null) return;
    onAdd(
      base.kind === "btree"
        ? { ...base, name: name.trim() }
        : { ...base, name: name.trim(), columns: shaped },
    );
  };
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className="dialog form wide"
        data-testid="table-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="tabs">
          <button
            type="button"
            data-testid="table-modal-tab-create"
            className={tab === "create" ? "active" : ""}
            onClick={() => dispatch({ kind: "tab", tab: "create" })}
          >
            {translate("newTable")}
          </button>
          <button
            type="button"
            data-testid="table-modal-tab-import"
            className={tab === "import" ? "active" : ""}
            onClick={() => dispatch({ kind: "tab", tab: "import" })}
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
              disabled={picked.kind === "picking"}
              onClick={() => void handleChoose()}
            >
              {picked.kind === "picking"
                ? translate("loading")
                : translate("chooseFile")}
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
              dispatch({ kind: "name", name: event.target.value })
            }
          />
        </div>

        {spec !== null && spec.kind === "xml" && (
          <div className="row">
            <div className="field">
              <label>{translate("rootElement")}</label>
              <input
                type="text"
                data-testid="table-modal-root"
                disabled={previewing}
                value={spec.rootElement}
                onChange={(event) =>
                  dispatch({
                    kind: "changeXml",
                    patch: {
                      rootElement: event.target.value,
                      recordElement: spec.recordElement,
                    },
                  })
                }
                onBlur={(event) =>
                  void rePreviewXml({
                    rootElement: event.target.value,
                  })
                }
              />
            </div>
            <div className="field">
              <label>{translate("recordElement")}</label>
              <input
                type="text"
                data-testid="table-modal-record"
                disabled={previewing}
                value={spec.recordElement}
                onChange={(event) =>
                  dispatch({
                    kind: "changeXml",
                    patch: {
                      rootElement: spec.rootElement,
                      recordElement: event.target.value,
                    },
                  })
                }
                onBlur={(event) =>
                  void rePreviewXml({
                    recordElement: event.target.value,
                  })
                }
              />
            </div>
          </div>
        )}

        {spec !== null && spec.kind === "csv" && (
          <div className="row">
            <div className="field">
              <label>{translate("separator")}</label>
              <select
                data-testid="table-modal-separator"
                disabled={previewing}
                value={spec.separator}
                onChange={(event) =>
                  void rePreviewCsv({
                    separator: event.target.value,
                  })
                }
              >
                {SEPARATOR_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>{translate("headerLine")}</label>
              <input
                type="number"
                data-testid="table-modal-header-line"
                min={1}
                disabled={previewing}
                value={String(spec.headerLine)}
                onChange={(event) => {
                  const parsed = Number.parseInt(event.target.value, 10);
                  if (Number.isFinite(parsed) && parsed >= 1)
                    void rePreviewCsv({
                      headerLine: parsed,
                    });
                }}
              />
            </div>
          </div>
        )}

        {spec !== null &&
          (spec.kind === "csv" || spec.kind === "xml") &&
          sampleRows.length > 0 && (
            <div className="field">
              <label>{translate("preview")}</label>
              <div className="import-preview">
                <table className="tuples" data-testid="table-modal-preview">
                  <thead>
                    <tr>
                      {columns.map((column, index) => (
                        <th key={index}>{column.name}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {sampleRows.map((row, rowAt) => (
                      <tr key={rowAt}>
                        {row.map((cell, cellAt) => (
                          <td key={cellAt}>{cell ?? ""}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

        {showGrid && (
          <div className="field">
            <label>{translate("columns")}</label>
            {columns.map((column, index) => (
              <div className="row" key={index}>
                <input
                  type="text"
                  data-testid="table-modal-column-name"
                  value={column.name}
                  placeholder={translate("columnName")}
                  onChange={(event) =>
                    dispatch({
                      kind: "patchColumn",
                      index,
                      patch: { name: event.target.value },
                    })
                  }
                />
                <select
                  data-testid="table-modal-column-type"
                  value={column.type}
                  onChange={(event) =>
                    dispatch({
                      kind: "patchColumn",
                      index,
                      patch: {
                        type: event.target.value as ColumnType,
                      },
                    })
                  }
                >
                  {COLUMN_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type.toLowerCase()}
                    </option>
                  ))}
                </select>
                <label className="check">
                  <input
                    type="checkbox"
                    data-testid="table-modal-column-pk"
                    checked={column.primaryKey}
                    onChange={(event) =>
                      dispatch({
                        kind: "patchColumn",
                        index,
                        patch: {
                          primaryKey: event.target.checked,
                        },
                      })
                    }
                  />
                  {translate("primaryKey")}
                </label>
                <button
                  type="button"
                  className="row-remove"
                  data-testid="table-modal-column-remove"
                  onClick={() =>
                    dispatch({
                      kind: "removeColumn",
                      index,
                    })
                  }
                >
                  ×
                </button>
              </div>
            ))}
            <button
              type="button"
              className="btn-add"
              data-testid="table-modal-add-column"
              onClick={() => dispatch({ kind: "addColumn" })}
            >
              {translate("addColumn")}
            </button>
          </div>
        )}

        {tab === "create" && (
          <div className="field">
            <label>{translate("rows")}</label>
            {rows.map((row, rowAt) => (
              <div className="row" key={rowAt}>
                {columns.map((column, columnAt) => (
                  <input
                    key={columnAt}
                    type="text"
                    data-testid="table-modal-cell"
                    className={
                      acceptsLiteral(column.type, row[columnAt] ?? "")
                        ? undefined
                        : "bad"
                    }
                    value={row[columnAt] ?? ""}
                    onChange={(event) =>
                      dispatch({
                        kind: "setCell",
                        row: rowAt,
                        column: columnAt,
                        text: event.target.value,
                      })
                    }
                  />
                ))}
                <button
                  type="button"
                  className="row-remove"
                  data-testid="table-modal-row-remove"
                  onClick={() =>
                    dispatch({
                      kind: "removeRow",
                      index: rowAt,
                    })
                  }
                >
                  ×
                </button>
              </div>
            ))}
            <button
              type="button"
              className="btn-add"
              data-testid="table-modal-add-row"
              onClick={() => dispatch({ kind: "addRow" })}
            >
              {translate("addRow")}
            </button>
          </div>
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
      </div>
    </div>
  );
}
