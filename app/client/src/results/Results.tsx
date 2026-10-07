import { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "../i18n";
import { EngineBusyError, messageOf, type Api } from "../server/api";
import { qualifiedName, showRawValue } from "../server/literals";
import type { FormValue, NodeId, SchemaColumn } from "../server/types";
import { useApi } from "../ui/connection";
import { Dialog } from "../ui/Dialog";
import { Loading } from "../ui/Status";
import { useLoad, type Async } from "../ui/useLoad";
import { useStep, type Dispatch } from "../ui/useStep";
import {
  atEnd,
  elapsedMs,
  failureOf,
  hasNextPage,
  initial,
  PAGE,
  pageNumber,
  running,
  shownRows,
  step,
  streaming,
  totalShown,
  type Effect,
  type Event,
  type Failure,
  type State,
} from "./results";

const STREAM_UPDATE_MS = 200;
const EXPORT_FORMATS = ["csv", "sql"] as const;

type Props = {
  sid: string;
  id: NodeId;
  onClose: () => void;
};

export function Results({ sid, id, onClose }: Props) {
  const translate = useTranslation();
  const api = useApi();
  const run = (
    effect: Effect,
    dispatch: Dispatch<Event>,
    signal: AbortSignal,
  ) => perform(api, sid, id, effect, dispatch, signal);
  const [state, dispatch] = useStep(step, initial, run);
  const loadSchema = useCallback(() => api.schema(sid, id), [api, sid, id]);
  const schema = useLoad(loadSchema).state;
  const body = useRef<HTMLDivElement>(null);
  const rows = shownRows(state);
  const headers = headersOf(schema, rows);
  const failure = failureOf(state);
  const total = totalShown(state);
  const elapsed = elapsedMs(state);
  useEffect(() => {
    body.current?.scrollTo({ top: 0, left: 0 });
  }, [state.mode, state.offset]);
  return (
    <Dialog className="results" testId="results" onClose={onClose}>
      <header>
        <h2>{translate("results")}</h2>
        {elapsed !== null && (
          <span className="faded">
            {translate("elapsed", { ms: Math.round(elapsed) })}
          </span>
        )}
        {total !== null && (
          <span className="faded" data-testid="results-total">
            {translate(total.confirmed ? "rowTotal" : "rowTotalPartial", {
              n: total.n,
            })}
          </span>
        )}
        <span className="faded" data-testid="results-page">
          {translate("currentPage", { n: pageNumber(state) })}
        </span>
        <span className="spacer" />
        {EXPORT_FORMATS.map((format) => (
          <a
            key={format}
            className="btn compact"
            href={api.exportUrl(sid, id, format)}
            download
          >
            {format.toUpperCase()}
          </a>
        ))}
        <button
          type="button"
          className="btn-ghost"
          data-testid="results-dismiss"
          onClick={onClose}
        >
          {translate("dismiss")}
        </button>
      </header>

      <div ref={body} className="results-body" data-testid="results-body">
        {failure !== null ? (
          <div className="centred">
            <p className={failure.busy ? "faded" : "detail"}>
              {failure.message}
            </p>
          </div>
        ) : running(state) ? (
          <Loading />
        ) : (
          <ResultsTable headers={headers} rows={rows} offset={state.offset} />
        )}
      </div>

      <footer>
        {streaming(state) ? (
          <span
            className="results-streaming"
            role="status"
            aria-label={translate("loading")}
          >
            <span className="loading-spinner" aria-hidden="true" />
          </span>
        ) : (
          <Pager state={state} dispatch={dispatch} />
        )}
      </footer>
    </Dialog>
  );
}

function headersOf(
  schema: Async<SchemaColumn[]>,
  rows: FormValue[][],
): string[] {
  if (schema.kind === "ready" && schema.value.length > 0)
    return schema.value.map(qualifiedName);
  const firstRow = rows[0] ?? [];
  return firstRow.map((_, index) => String(index));
}

type ResultsTableProps = {
  headers: string[];
  rows: FormValue[][];
  offset: number;
};

function ResultsTable({ headers, rows, offset }: ResultsTableProps) {
  return (
    <table className="tuples" data-testid="results-table">
      <thead>
        <tr>
          {headers.map((header, columnIndex) => (
            <th key={`${header}-${columnIndex}`}>{header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, rowIndex) => (
          <tr key={offset + rowIndex}>
            {headers.map((header, columnIndex) => (
              <td key={`${header}-${columnIndex}`}>
                {showRawValue(row[columnIndex])}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

type PagerProps = {
  state: State;
  dispatch: Dispatch<Event>;
};

function Pager({ state, dispatch }: PagerProps) {
  const translate = useTranslation();
  const atStart = state.offset === 0;
  return (
    <>
      <button
        type="button"
        className="btn-ghost"
        data-testid="results-first"
        disabled={atStart}
        title={translate("firstPage")}
        onClick={() => dispatch({ kind: "firstClicked" })}
      >
        «
      </button>
      <button
        type="button"
        className="btn-ghost"
        data-testid="results-prev"
        disabled={atStart}
        onClick={() => dispatch({ kind: "previousClicked" })}
      >
        {translate("previous")}
      </button>
      <button
        type="button"
        className="btn-ghost"
        data-testid="results-next"
        disabled={!hasNextPage(state)}
        onClick={() => dispatch({ kind: "nextClicked" })}
      >
        {translate("next")}
      </button>
      <button
        type="button"
        className="btn-ghost"
        data-testid="results-all"
        disabled={atEnd(state)}
        title={translate("allRows")}
        onClick={() => dispatch({ kind: "allClicked" })}
      >
        »
      </button>
    </>
  );
}

function perform(
  api: Api,
  sid: string,
  id: NodeId,
  effect: Effect,
  dispatch: Dispatch<Event>,
  signal: AbortSignal,
): void {
  switch (effect.kind) {
    case "fetchPage":
      void api.rows(sid, id, effect.offset, PAGE).then(
        (page) => dispatch({ kind: "pageLoaded", offset: effect.offset, page }),
        (caught: unknown) =>
          dispatch({
            kind: "pageFailed",
            offset: effect.offset,
            failure: failureFrom(caught),
          }),
      );
      return;
    case "stream":
      void streamAll(api, sid, id, dispatch, signal);
      return;
  }
}

async function streamAll(
  api: Api,
  sid: string,
  id: NodeId,
  dispatch: Dispatch<Event>,
  signal: AbortSignal,
): Promise<void> {
  const started = performance.now();
  const rows: FormValue[][] = [];
  let lastUpdate = 0;
  try {
    for await (const batch of api.rowsAllStream(sid, id, signal)) {
      for (const row of batch) rows.push(row);
      const now = performance.now();
      if (now - lastUpdate >= STREAM_UPDATE_MS) {
        lastUpdate = now;
        dispatch({
          kind: "streamProgressed",
          rows: [...rows],
          elapsedMs: now - started,
        });
      }
    }
    dispatch({
      kind: "streamFinished",
      rows: [...rows],
      elapsedMs: performance.now() - started,
    });
  } catch (caught) {
    dispatch({ kind: "streamFailed", failure: failureFrom(caught) });
  }
}

function failureFrom(caught: unknown): Failure {
  return {
    message: messageOf(caught),
    busy: caught instanceof EngineBusyError,
  };
}
