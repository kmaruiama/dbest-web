import { useTranslation } from "../i18n";
import type { CsvTable } from "../server/types";
import type { Dispatch } from "../ui/useStep";
import type { Event } from "./tables";

type Props = {
  spec: CsvTable;
  previewing: boolean;
  dispatch: Dispatch<Event>;
};

export function CsvOptions({ spec, previewing, dispatch }: Props) {
  const translate = useTranslation();
  return (
    <div className="row">
      <div className="field">
        <label>{translate("separator")}</label>
        <select
          data-testid="table-modal-separator"
          disabled={previewing}
          value={spec.separator}
          onChange={(event) =>
            dispatch({
              kind: "csvOptionsChosen",
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
              dispatch({ kind: "csvOptionsChosen", headerLine: parsed });
          }}
        />
      </div>
    </div>
  );
}

const SEPARATOR_OPTIONS: {
  value: string;
  label: string;
}[] = [
  { value: ",", label: "," },
  { value: ";", label: ";" },
  { value: "\t", label: "tab" },
  { value: "|", label: "|" },
];
