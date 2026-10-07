import { useTranslation } from "../i18n";
import type { Column, ColumnType } from "../server/types";
import { COLUMN_TYPES } from "../server/literals";
import type { Dispatch } from "../ui/useStep";
import type { Event } from "./tables";

type Props = {
  columns: Column[];
  dispatch: Dispatch<Event>;
};

export function ColumnsGrid({ columns, dispatch }: Props) {
  const translate = useTranslation();
  return (
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
                kind: "columnEdited",
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
                kind: "columnEdited",
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
                  kind: "columnEdited",
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
                kind: "columnRemoved",
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
        onClick={() => dispatch({ kind: "columnAdded" })}
      >
        {translate("addColumn")}
      </button>
    </div>
  );
}
