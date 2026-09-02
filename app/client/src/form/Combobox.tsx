import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { rankColumns } from "./rankColumns";

type Props = {
  value: string;
  onChange: (value: string) => void;
  columns: string[];
  placeholder?: string;
};

type Spot = {
  left: number;
  top: number;
  width: number;
  above: boolean;
};

const MAX_HEIGHT = 180;

export function Combobox({ value, onChange, columns, placeholder }: Props) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [spot, setSpot] = useState<Spot | null>(null);
  const anchor = useRef<HTMLInputElement>(null);
  const options = rankColumns(columns, value);
  const showing = open && options.length > 0;
  const place = useCallback(() => {
    const input = anchor.current;
    if (input === null) return;
    const rect = input.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom;
    const above = below < MAX_HEIGHT && rect.top > below;
    setSpot({
      left: rect.left,
      top: above ? rect.top - 2 : rect.bottom + 2,
      width: rect.width,
      above,
    });
  }, []);
  const handleChoose = (column: string) => {
    onChange(column);
    setActive(-1);
  };
  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (!showing) {
      if (event.key === "ArrowDown") setOpen(true);
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => (index + 1) % options.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => (index <= 0 ? options.length - 1 : index - 1));
    } else if (event.key === "Enter" && active >= 0) {
      event.preventDefault();
      handleChoose(options[active]);
    } else if (event.key === "Escape") {
      event.stopPropagation();
      setOpen(false);
      setActive(-1);
    }
  };
  useLayoutEffect(() => {
    if (showing) place();
  }, [showing, place, options.length]);
  useEffect(() => {
    if (!showing) return;
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [showing, place]);
  return (
    <>
      <input
        ref={anchor}
        type="text"
        role="combobox"
        aria-expanded={showing}
        aria-controls={listId}
        value={value}
        placeholder={placeholder}
        onChange={(event) => {
          onChange(event.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={handleKeyDown}
      />
      {showing &&
        spot !== null &&
        createPortal(
          <ul
            id={listId}
            role="listbox"
            className="combobox-list"
            style={{
              left: spot.left,
              width: spot.width,
              ...(spot.above
                ? {
                    bottom: window.innerHeight - spot.top,
                    top: "auto",
                  }
                : { top: spot.top }),
            }}
          >
            {options.map((column, index) => (
              <li
                key={column}
                role="option"
                aria-selected={index === active}
                className={
                  index === active
                    ? "combobox-option active"
                    : "combobox-option"
                }
                onMouseEnter={() => setActive(index)}
                onMouseDown={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  handleChoose(column);
                }}
              >
                {column}
              </li>
            ))}
          </ul>,
          document.body,
        )}
    </>
  );
}
