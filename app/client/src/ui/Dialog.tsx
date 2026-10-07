import { useEffect, type ReactNode } from "react";

type Props = {
  className?: string;
  testId?: string;
  onClose: () => void;
  children: ReactNode;
};

export function Dialog({ className, testId, onClose, children }: Props) {
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className={className === undefined ? "dialog" : `dialog ${className}`}
        data-testid={testId}
        onMouseDown={(event) => event.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}
