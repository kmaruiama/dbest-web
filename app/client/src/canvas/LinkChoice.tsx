import { useTranslation } from "../i18n";
import type { Edge } from "../server/types";

type Props = {
  options: Edge[];
  onChoose: (link: Edge) => void;
  onCancel: () => void;
};

export function LinkChoice({ options, onChoose, onCancel }: Props) {
  const translate = useTranslation();
  return (
    <div className="overlay" onMouseDown={onCancel}>
      <div className="dialog" onMouseDown={(event) => event.stopPropagation()}>
        <h2>{translate("linkWhichInput")}</h2>
        <div className="dialog-choices">
          {options.map((option) => (
            <button
              key={option.port}
              type="button"
              className="btn"
              data-testid={`port-${option.port}`}
              onClick={() => onChoose(option)}
            >
              {translate(`port.${option.port}`)}
            </button>
          ))}
        </div>
        <button type="button" className="btn-ghost" onClick={onCancel}>
          {translate("cancel")}
        </button>
      </div>
    </div>
  );
}
