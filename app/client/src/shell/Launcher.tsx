import { useTranslation } from "../i18n";
import { useNewSessionDialog } from "./NameDialog";
import type { Board, Sessions } from "./useSessions";

type Props = {
  sessions: Sessions;
  board: Board;
};

export function Launcher({ sessions, board }: Props) {
  const translate = useTranslation();
  const { start, dialog } = useNewSessionDialog(sessions, board);
  const { config, files } = board;
  return (
    <div className="launcher">
      <div className="launcher-card">
        {config.sessionsDir === null ? (
          <>
            <h2>{translate("launcherChooseDirIntro")}</h2>
            <button type="button" className="btn" onClick={sessions.chooseDir}>
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
                onClick={start}
              >
                {translate("launcherStartFresh")}
              </button>
              <button
                type="button"
                className="toolbtn"
                onClick={sessions.chooseDir}
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
                      onClick={() => sessions.open(file.path)}
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

      {dialog}
    </div>
  );
}
