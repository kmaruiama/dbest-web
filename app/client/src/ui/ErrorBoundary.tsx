import { Component, type ErrorInfo, type ReactNode } from "react";
import { useTranslation } from "../i18n";

type Props = {
  children: ReactNode;
};

type State = {
  error: Error | null;
};

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };
  static getDerivedStateFromError(error: Error): State {
    return { error };
  }
  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("uncaught render error", error, info.componentStack);
  }
  render() {
    if (this.state.error === null) return this.props.children;
    return <Crashed message={this.state.error.message} />;
  }
}

type CrashedProps = {
  message: string;
};

function Crashed({ message }: CrashedProps) {
  const translate = useTranslation();
  return (
    <div className="centred" data-testid="app-crashed">
      <p>{translate("appCrashed")}</p>
      <p className="detail">{message}</p>
      <button
        type="button"
        className="btn"
        onClick={() => window.location.reload()}
      >
        {translate("reloadApp")}
      </button>
    </div>
  );
}
