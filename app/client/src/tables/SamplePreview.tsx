import { useTranslation } from "../i18n";
import type { Column } from "../server/types";

type Props = {
  columns: Column[];
  sampleRows: (string | null)[][];
};

export function SamplePreview({ columns, sampleRows }: Props) {
  const translate = useTranslation();
  return (
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
  );
}
