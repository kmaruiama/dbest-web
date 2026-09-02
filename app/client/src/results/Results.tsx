import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useAsync } from "../hooks/useAsync";
import { useTranslation } from "../i18n";
import { api, EngineBusyError, messageOf } from "../server/api";
import { showColumnRef, showRawValue } from "../server/literals";
import type { NodeId, RowsPage, SchemaColumn } from "../server/types";
import type { FormValue } from "../form/FormDraft";
import { Loading } from "../ui/Status";

const NO_SCHEMA: SchemaColumn[] = [];
const PAGE = 50;
const STREAM_UPDATE_MS = 200;

type Props = {
  sid: string;
  id: NodeId;
  onClose: () => void;
};

type Load =
  | {
      kind: "running";
    }
  | {
      kind: "failed";
      message: string;
      busy: boolean;
    }
  | {
      kind: "ready";
      page: RowsPage;
    };

type Summary = {
  total: number;
  elapsedMs: number;
  complete: boolean;
};

type Progress = {
  load: Load;
  summary: Summary | null;
  streaming: boolean;
};

const LOADING: Progress = {
  load: { kind: "running" },
  summary: null,
  streaming: false,
};

type Step =
  | {
      kind: "page";
    }
  | {
      kind: "stream";
    }
  | {
      kind: "pageReady";
      page: RowsPage;
    }
  | {
      kind: "tick";
      page: RowsPage;
    }
  | {
      kind: "streamed";
      page: RowsPage;
    }
  | {
      kind: "failed";
      message: string;
      busy: boolean;
    };

function advance(state: Progress, step: Step): Progress {
  switch (step.kind) {
    case "page":
      return {
        load: { kind: "running" },
        summary: state.summary,
        streaming: false,
      };
    case "stream":
      return {
        load: { kind: "running" },
        summary: null,
        streaming: true,
      };
    case "pageReady":
      return {
        load: { kind: "ready", page: step.page },
        summary: state.summary,
        streaming: false,
      };
    case "tick":
      return {
        load: { kind: "ready", page: step.page },
        summary: {
          total: step.page.rows.length,
          elapsedMs: step.page.elapsedMs,
          complete: false,
        },
        streaming: true,
      };
    case "streamed":
      return {
        load: { kind: "ready", page: step.page },
        summary: {
          total: step.page.rows.length,
          elapsedMs: step.page.elapsedMs,
          complete: true,
        },
        streaming: false,
      };
    case "failed":
      return {
        load: {
          kind: "failed",
          message: step.message,
          busy: step.busy,
        },
        summary: state.summary,
        streaming: false,
      };
  }
}

export function Results({ sid, id, onClose }: Props) {
  const translate = useTranslation();
  const [view, setView] = useState<number | "all">(0);
  const [windowOffset, setWindowOffset] = useState(0);
  const [{ load, summary, streaming }, dispatch] = useReducer(advance, LOADING);
  const body = useRef<HTMLDivElement>(null);
  const loadSchema = useCallback(() => api.schema(sid, id), [sid, id]);
  const schemaState = useAsync(loadSchema, [sid, id]);
  const schema =
    schemaState.state.kind === "ready" ? schemaState.state.value : NO_SCHEMA;
  const loadedRows = load.kind === "ready" ? load.page.rows : [];
  const headers =
    schema.length > 0
      ? schema.map(showColumnRef)
      : (loadedRows[0] ?? []).map((_, index) => String(index));
  const offset = view === "all" ? windowOffset : view;
  const displayRows =
    view === "all" ? loadedRows.slice(offset, offset + PAGE) : loadedRows;
  const shown = displayRows.length;
  const atStart = offset === 0;
  const hasNextPage =
    load.kind === "ready" &&
    (view === "all"
      ? summary?.complete === true && offset + PAGE < summary.total
      : shown >= PAGE);
  const atEnd = summary?.complete === true && offset + PAGE >= summary.total;
  const shownTotal = summary?.total ?? offset + loadedRows.length;
  const confirmedTotal =
    summary?.complete === true ||
    (summary === null && load.kind === "ready" && shown < PAGE);
  const elapsedMs =
    summary?.elapsedMs ?? (load.kind === "ready" ? load.page.elapsedMs : null);
  const currentPage = Math.floor(offset / PAGE) + 1;
  const goTo = (target: number) =>
    view === "all" ? setWindowOffset(target) : setView(target);
  const handleFirst = () => goTo(0);
  const handlePrev = () => goTo(Math.max(0, offset - PAGE));
  const handleNext = () => goTo(offset + PAGE);
  const handleShowAll = () => {
    if (summary?.complete === true) {
      setWindowOffset(
        summary.total === 0 ? 0 : Math.floor((summary.total - 1) / PAGE) * PAGE,
      );
      return;
    }
    dispatch({ kind: "stream" });
    setView("all");
  };
  useEffect(() => {
    let live = true;
    const controller = new AbortController();
    const fail = (caught: unknown) => {
      if (live) {
        dispatch({
          kind: "failed",
          message: messageOf(caught),
          busy: caught instanceof EngineBusyError,
        });
      }
    };
    const fetchPaged = async (target: number) => {
      dispatch({ kind: "page" });
      try {
        const page = await api.rows(sid, id, target, PAGE);
        if (live) dispatch({ kind: "pageReady", page });
      } catch (caught) {
        fail(caught);
      }
    };
    const fetchAll = async () => {
      dispatch({ kind: "stream" });
      const started = performance.now();
      const rows: FormValue[][] = [];
      let lastUpdate = 0;
      try {
        for await (const batch of api.rowsAllStream(
          sid,
          id,
          controller.signal,
        )) {
          if (!live) return;
          for (const row of batch) rows.push(row);
          const now = performance.now();
          if (now - lastUpdate >= STREAM_UPDATE_MS) {
            lastUpdate = now;
            dispatch({
              kind: "tick",
              page: { rows, elapsedMs: now - started },
            });
          }
        }
        if (live) {
          const elapsedMs = performance.now() - started;
          const lastPageOffset =
            rows.length === 0 ? 0 : Math.floor((rows.length - 1) / PAGE) * PAGE;
          setWindowOffset(lastPageOffset);
          dispatch({ kind: "streamed", page: { rows, elapsedMs } });
        }
      } catch (caught) {
        fail(caught);
      }
    };
    void (view === "all" ? fetchAll() : fetchPaged(view));
    return () => {
      live = false;
      controller.abort();
    };
  }, [sid, id, view]);
  useEffect(() => {
    body.current?.scrollTo({ top: 0, left: 0 });
  }, [view, windowOffset]);
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className="dialog results"
        data-testid="results"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <h2>{translate("results")}</h2>
          {elapsedMs !== null && (
            <span className="faded">
              {translate("elapsed", {
                ms: Math.round(elapsedMs),
              })}
            </span>
          )}
          {(summary !== null || load.kind === "ready") && (
            <span className="faded" data-testid="results-total">
              {translate(confirmedTotal ? "rowTotal" : "rowTotalPartial", {
                n: shownTotal,
              })}
            </span>
          )}
          <span className="faded" data-testid="results-page">
            {translate("currentPage", { n: currentPage })}
          </span>
          <span className="spacer" />
          <a className="btn" href={api.exportUrl(sid, id, "csv")} download>
            CSV
          </a>
          <a className="btn" href={api.exportUrl(sid, id, "sql")} download>
            SQL
          </a>
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
          {load.kind === "running" && <Loading />}
          {load.kind === "failed" && (
            <div className="centred">
              <p className={load.busy ? "faded" : "detail"}>{load.message}</p>
            </div>
          )}
          {load.kind === "ready" && (
            <table className="tuples" data-testid="results-table">
              <thead>
                <tr>
                  {headers.map((header, columnIndex) => (
                    <th key={`${header}-${columnIndex}`}>{header}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {displayRows.map((row, rowIndex) => (
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
          )}
        </div>

        <footer>
          {streaming ? (
            <span
              className="results-streaming"
              role="status"
              aria-label={translate("loading")}
            >
              <span className="loading-spinner" aria-hidden="true" />
            </span>
          ) : (
            <>
              <button
                type="button"
                className="btn-ghost"
                data-testid="results-first"
                disabled={atStart}
                title={translate("firstPage")}
                onClick={handleFirst}
              >
                «
              </button>
              <button
                type="button"
                className="btn-ghost"
                data-testid="results-prev"
                disabled={atStart}
                onClick={handlePrev}
              >
                {translate("previous")}
              </button>
              <button
                type="button"
                className="btn-ghost"
                data-testid="results-next"
                disabled={!hasNextPage}
                onClick={handleNext}
              >
                {translate("next")}
              </button>
              <button
                type="button"
                className="btn-ghost"
                data-testid="results-all"
                disabled={atEnd}
                title={translate("allRows")}
                onClick={handleShowAll}
              >
                »
              </button>
            </>
          )}
        </footer>
      </div>
    </div>
  );
}
