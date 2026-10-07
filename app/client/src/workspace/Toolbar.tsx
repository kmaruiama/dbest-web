import { useTranslation } from "../i18n";
import type { NodeId, SessionView } from "../server/types";
import type { Dispatch } from "../ui/useStep";
import type { Event } from "./workspace";

type Props = {
  view: SessionView;
  marks: NodeId[];
  marksApart: boolean;
  dispatch: Dispatch<Event>;
};

export function Toolbar({ view, marks, marksApart, dispatch }: Props) {
  const translate = useTranslation();
  return (
    <div className="workspace-toolbar">
      <button
        type="button"
        className="btn-ghost compact"
        disabled={view.undoDepth === 0}
        onClick={() => dispatch({ kind: "undoClicked" })}
      >
        ↶ {translate("undo")}
      </button>
      <button
        type="button"
        className="btn-ghost compact"
        disabled={view.redoDepth === 0}
        onClick={() => dispatch({ kind: "redoClicked" })}
      >
        ↷ {translate("redo")}
      </button>
      <span className="revision">
        {translate("stepsLabel", { n: view.undoDepth })}
      </span>
      <span className="spacer" />
      {marks.length === 1 && (
        <span className="toolbar-hint">{translate("markHint")}</span>
      )}
      {marks.length === 2 && (
        <button
          type="button"
          className="btn compact"
          data-testid="compare"
          disabled={!marksApart}
          title={marksApart ? undefined : translate("compare.sameTree")}
          onClick={() => dispatch({ kind: "compareClicked" })}
        >
          ⇄ {translate("compare")} <span className="mark-letter">A</span>
          <span className="mark-letter">B</span>
        </button>
      )}
      {marks.length > 0 && (
        <button
          type="button"
          className="btn-ghost compact"
          data-testid="clear-marks"
          title={translate("clearMarks")}
          onClick={() => dispatch({ kind: "clearMarksClicked" })}
        >
          ×
        </button>
      )}
    </div>
  );
}
