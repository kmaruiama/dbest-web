import { useTranslation } from "../i18n";

export function Loading() {
  const translate = useTranslation();
  return <div className="centred">{translate("loading")}</div>;
}

type ErrorBarProps = {
  message: string;
  onDismiss: () => void;
};

export function ErrorBar({ message, onDismiss }: ErrorBarProps) {
  const translate = useTranslation();
  return (
    <div className="error-bar" data-testid="error-bar">
      <span>{message}</span>
      <button type="button" className="btn-ghost" onClick={onDismiss}>
        {translate("dismiss")}
      </button>
    </div>
  );
}

type FailureProps = {
  message: string;
  onRetry?: () => void;
};

export function Failure({ message, onRetry }: FailureProps) {
  const translate = useTranslation();
  return (
    <div className="centred">
      <p>{translate("loadFailed")}</p>
      <p className="detail">{message}</p>
      {onRetry !== undefined && (
        <button type="button" className="btn" onClick={onRetry}>
          {translate("retry")}
        </button>
      )}
    </div>
  );
}
