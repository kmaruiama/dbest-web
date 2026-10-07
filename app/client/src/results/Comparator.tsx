import { useState } from "react";
import { useLang, useTranslation } from "../i18n";
import { messageOf, type Api } from "../server/api";
import type { NodeId } from "../server/types";
import { useApi } from "../ui/connection";
import { Dialog } from "../ui/Dialog";
import { Loading } from "../ui/Status";
import { useStep, type Dispatch } from "../ui/useStep";
import {
  DEFAULT_LIMIT,
  initial,
  metricRows,
  step,
  type Effect,
  type Event,
  type MetricRow,
  type MetricUnit,
} from "./comparator";

type Side = { id: NodeId; letter: string; title: string };

type Props = {
  sid: string;
  sides: [Side, Side];
  onClose: () => void;
};

export function Comparator({ sid, sides, onClose }: Props) {
  const translate = useTranslation();
  const api = useApi();
  const lang = useLang();
  const [a, b] = sides;
  const run = (effect: Effect, dispatch: Dispatch<Event>) =>
    perform(api, sid, a.id, b.id, effect, dispatch);
  const [compared, dispatch] = useStep(step, initial, run);
  const state = compared.measures;
  const [draft, setDraft] = useState(String(DEFAULT_LIMIT));
  const typed = Number(draft);
  const valid = Number.isInteger(typed) && typed > 0;
  const handleMeasure = () => {
    if (valid) dispatch({ kind: "measureClicked", limit: typed });
  };
  return (
    <Dialog className="comparator" testId="comparator" onClose={onClose}>
      <header>
        <h2>{translate("comparator")}</h2>
        <span className="spacer" />
        <button type="button" className="btn-ghost compact" onClick={onClose}>
          {translate("dismiss")}
        </button>
      </header>

      <div className="comparator-budget">
        <label htmlFor="comparator-limit">{translate("tuplesToRead")}</label>
        <input
          id="comparator-limit"
          type="number"
          min={1}
          value={draft}
          data-testid="comparator-limit"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") handleMeasure();
          }}
        />
        <button
          type="button"
          className="btn compact"
          data-testid="comparator-measure"
          disabled={!valid || state.kind === "loading"}
          onClick={handleMeasure}
        >
          {translate("measure")}
        </button>
        <button
          type="button"
          className="btn-ghost compact"
          data-testid="comparator-all"
          disabled={state.kind === "loading"}
          onClick={() => dispatch({ kind: "measureClicked", limit: null })}
        >
          {translate("allRows")}
        </button>
      </div>

      <div className="results-body">
        {state.kind === "notMeasured" && (
          <div className="centred" data-testid="comparator-not-measured">
            <p className="faded">{translate("notMeasured")}</p>
          </div>
        )}
        {state.kind === "loading" && <Loading />}
        {state.kind === "failed" && (
          <div className="centred">
            <p className="detail">{state.message}</p>
          </div>
        )}
        {state.kind === "ready" && (
          <table className="tuples comparator-table">
            <thead>
              <tr>
                <th />
                {sides.map((side) => (
                  <th key={side.id}>
                    <span className="mark-letter">{side.letter}</span>
                    {side.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {metricRows(state.value).map((row) => (
                <tr key={row.key} data-testid={row.key}>
                  <th>{translate(row.key)}</th>
                  {row.values.map((value, index) => (
                    <td
                      key={sides[index].id}
                      className={isLower(row, index) ? "lower" : undefined}
                    >
                      {metricText(row.unit, value, lang) +
                        (row.partial[index] ? "+" : "")}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <footer>
        <span className="faded">{translate("comparatorNote")}</span>
      </footer>
    </Dialog>
  );
}

function isLower(row: MetricRow, index: number): boolean {
  const other = row.values[1 - index];
  return row.ranked && row.values[index] < other;
}

function metricText(unit: MetricUnit, value: number, lang: string): string {
  switch (unit) {
    case "count":
      return localeNumber(value, lang, 0);
    case "ms":
      return `${localeNumber(value, lang, 2)} ms`;
    case "bytes":
      return `${localeNumber(value / 1024, lang, 2)} KB`;
  }
}

function localeNumber(value: number, lang: string, digits: number): string {
  return value.toLocaleString(lang, { maximumFractionDigits: digits });
}

function perform(
  api: Api,
  sid: string,
  a: NodeId,
  b: NodeId,
  effect: Effect,
  dispatch: Dispatch<Event>,
): void {
  void api.compare(sid, a, b, effect.limit).then(
    (measures) => dispatch({ kind: "measured", asked: effect.asked, measures }),
    (caught: unknown) =>
      dispatch({
        kind: "measureFailed",
        asked: effect.asked,
        message: messageOf(caught),
      }),
  );
}
