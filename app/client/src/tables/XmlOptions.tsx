import { useTranslation } from "../i18n";
import type { XmlTable } from "../server/types";
import type { Dispatch } from "../ui/useStep";
import type { Event } from "./tables";

type Props = {
  spec: XmlTable;
  previewing: boolean;
  dispatch: Dispatch<Event>;
};

export function XmlOptions({ spec, previewing, dispatch }: Props) {
  const translate = useTranslation();
  return (
    <div className="row">
      <div className="field">
        <label>{translate("rootElement")}</label>
        <input
          type="text"
          data-testid="table-modal-root"
          disabled={previewing}
          value={spec.rootElement ?? ""}
          onChange={(event) =>
            dispatch({
              kind: "xmlTyped",
              elements: {
                rootElement: event.target.value,
                recordElement: spec.recordElement,
              },
            })
          }
          onBlur={(event) =>
            dispatch({
              kind: "xmlLeft",
              elements: { rootElement: event.target.value },
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
          value={spec.recordElement ?? ""}
          onChange={(event) =>
            dispatch({
              kind: "xmlTyped",
              elements: {
                rootElement: spec.rootElement,
                recordElement: event.target.value,
              },
            })
          }
          onBlur={(event) =>
            dispatch({
              kind: "xmlLeft",
              elements: { recordElement: event.target.value },
            })
          }
        />
      </div>
    </div>
  );
}
