import { useCallback, useEffect, useState } from "react";
import { useAsync, type Async } from "../hooks/useAsync";
import { api, messageOf } from "../server/api";
import type { ConfigInfo, FileEntry, SessionMeta } from "../server/types";

export type Board = {
  config: ConfigInfo;
  files: FileEntry[];
  tabs: SessionMeta[];
  active: string | null;
};

const NO_FILES: FileEntry[] = [];
const AUTOSAVE_DELAY_MS = 2000;

export type Sessions = {
  load: Async<Board>;
  error: string | null;
  select: (sid: string) => void;
  showLauncher: () => void;
  create: (name: string) => void;
  open: (path: string) => void;
  close: (sid: string) => void;
  save: (sid: string) => void;
  rename: (sid: string, name: string) => void;
  markDirty: (sid: string) => void;
  chooseDir: () => void;
  reload: () => void;
  dismissError: () => void;
};

export function useSessions(): Sessions {
  const [error, setError] = useState<string | null>(null);
  const loadBoard = useCallback(async (): Promise<Board> => {
    const [config, tabs, files] = await Promise.all([
      api.config(),
      api.listSessions(),
      api.listFiles().catch(() => NO_FILES),
    ]);
    return { config, files, tabs, active: tabs.at(-1)?.sid ?? null };
  }, []);
  const { state, reload, patch } = useAsync(loadBoard, []);
  const board = state.kind === "ready" ? state.value : null;
  const attempt = useCallback(async (action: () => Promise<void>) => {
    try {
      await action();
    } catch (caught) {
      setError(messageOf(caught));
    }
  }, []);
  const refreshFiles = useCallback(() => {
    void api.listFiles().then(
      (files) => patch((current) => ({ ...current, files })),
      () => patch((current) => ({ ...current, files: NO_FILES })),
    );
  }, [patch]);
  const save = useCallback(
    (sid: string) => {
      void attempt(async () => {
        const saved = await api.saveSession(sid);
        patch((current) => ({
          ...current,
          tabs: current.tabs.map((tab) => (tab.sid === sid ? saved : tab)),
        }));
      });
    },
    [attempt, patch],
  );
  const rename = useCallback(
    (sid: string, name: string) => {
      void attempt(async () => {
        const renamed = await api.renameSession(sid, name);
        patch((current) => ({
          ...current,
          tabs: current.tabs.map((tab) => (tab.sid === sid ? renamed : tab)),
        }));
      });
    },
    [attempt, patch],
  );
  const markDirty = useCallback(
    (sid: string) => {
      patch((current) => ({
        ...current,
        tabs: current.tabs.map((tab) =>
          tab.sid === sid && !tab.dirty ? { ...tab, dirty: true } : tab,
        ),
      }));
    },
    [patch],
  );
  const create = useCallback(
    (name: string) => {
      void attempt(async () => {
        const meta = await api.newSession();
        const named = await api
          .saveSession(meta.sid, name)
          .catch(async (caught: unknown) => {
            await api.closeSession(meta.sid).catch(() => undefined);
            throw caught;
          });
        patch((current) => ({
          ...current,
          tabs: [...current.tabs, named],
          active: named.sid,
        }));
        refreshFiles();
      });
    },
    [attempt, patch, refreshFiles],
  );
  const close = useCallback(
    (sid: string) => {
      void attempt(async () => {
        if ((board?.tabs ?? []).some((tab) => tab.sid === sid && tab.dirty))
          await api.saveSession(sid);
        await api.closeSession(sid);
        patch((current) => {
          const left = current.tabs.filter((tab) => tab.sid !== sid);
          return {
            ...current,
            tabs: left,
            active:
              current.active === sid
                ? (left.at(-1)?.sid ?? null)
                : current.active,
          };
        });
      });
    },
    [attempt, patch, board],
  );
  const open = useCallback(
    (path: string) => {
      void attempt(async () => {
        const existing = (board?.tabs ?? []).find((tab) => tab.file === path);
        if (existing !== undefined) {
          patch((current) => ({ ...current, active: existing.sid }));
          return;
        }
        const meta = await api.openSession(path);
        patch((current) => ({
          ...current,
          tabs: [...current.tabs, meta],
          active: meta.sid,
        }));
      });
    },
    [attempt, patch, board],
  );
  const chooseDir = useCallback(() => {
    void attempt(async () => {
      const config = await api.chooseDir();
      patch((current) => ({ ...current, config }));
      refreshFiles();
    });
  }, [attempt, patch, refreshFiles]);
  const select = useCallback(
    (sid: string) => patch((current) => ({ ...current, active: sid })),
    [patch],
  );
  const showLauncher = useCallback(
    () => patch((current) => ({ ...current, active: null })),
    [patch],
  );
  useEffect(() => {
    const dirty = (board?.tabs ?? []).filter((tab) => tab.dirty);
    if (dirty.length === 0) return;
    const timer = setTimeout(() => {
      for (const tab of dirty) save(tab.sid);
    }, AUTOSAVE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [board, save]);
  return {
    load: state,
    error,
    select,
    showLauncher,
    create,
    open,
    close,
    save,
    rename,
    markDirty,
    chooseDir,
    reload,
    dismissError: () => setError(null),
  };
}
