import { useTranslation } from "../i18n";
import type { Dispatch } from "../ui/useStep";
import type { Board, Event } from "./app";

type Props = {
  board: Board;
  dispatch: Dispatch<Event>;
};

export function Launcher({ board, dispatch }: Props) {
  const translate = useTranslation();
  const { config, files } = board;
  return (
    <div className="launcher">
      <div className="launcher-card">
        {config.sessionsDir === null ? (
          <>
            <h2>{translate("launcherChooseDirIntro")}</h2>
            <button
              type="button"
              className="btn"
              onClick={() => dispatch({ kind: "folderClicked" })}
            >
              {translate("launcherChooseDir")}
            </button>
          </>
        ) : (
          <>
            <div className="launcher-actions">
              <button
                type="button"
                className="btn"
                data-testid="launcher-fresh"
                onClick={() => dispatch({ kind: "newSessionClicked" })}
              >
                {translate("launcherStartFresh")}
              </button>
              <button
                type="button"
                className="btn-ghost"
                onClick={() => dispatch({ kind: "folderClicked" })}
              >
                {translate("launcherChangeDir")}
              </button>
            </div>
            <p className="launcher-dir">
              {translate("launcherDirLabel", {
                dir: config.sessionsDir ?? "",
              })}
            </p>

            <h4>{translate("launcherOpenSaved")}</h4>
            {files.length === 0 ? (
              <p className="faded">{translate("launcherNoFiles")}</p>
            ) : (
              <ul className="launcher-files">
                {files.map((file) => (
                  <li key={file.path}>
                    <button
                      type="button"
                      className="launcher-file"
                      onClick={() =>
                        dispatch({ kind: "fileClicked", path: file.path })
                      }
                    >
                      {file.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </div>
  );
}
