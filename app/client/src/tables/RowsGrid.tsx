import { useTranslation } from "../i18n";
import type { Column } from "../server/types";
import { acceptsLiteral } from "../server/literals";
import type { Dispatch } from "../ui/useStep";
import type { Event } from "./tables";

type Props = {
  columns: Column[];
  rows: string[][];
  dispatch: Dispatch<Event>;
};

export function RowsGrid({ columns, rows, dispatch }: Props) {
  const translate = useTranslation();
  return (
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
                  kind: "cellTyped",
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
                kind: "rowRemoved",
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
        onClick={() => dispatch({ kind: "rowAdded" })}
      >
        {translate("addRow")}
      </button>
    </div>
  );
}
