import { useRef, useState, type PointerEvent } from "react";
import { humanize, useLabel, useTranslation } from "../i18n";
import { categories, chipsIn } from "../server/catalog";
import type { Session, TableId } from "../server/types";
import { useSettings } from "../shell/settings";
import { CHIP_MIME, TABLE_MIME } from "./dnd";

type Props = {
  session: Session;
  onAddScan: (id: TableId) => void;
  onRemoveTable: (id: TableId) => void;
  onDeselect: () => void;
};

const WIDTH_KEY = "dbest.palette.width";
const DEFAULT_WIDTH = 288;
const MIN_WIDTH = 180;
const MAX_WIDTH = 620;

const clampWidth = (value: number) =>
  Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, Math.round(value)));

function initialWidth(): number {
  const stored = Number(localStorage.getItem(WIDTH_KEY));
  return stored > 0 ? clampWidth(stored) : DEFAULT_WIDTH;
}

export function Palette({
  session,
  onAddScan,
  onRemoveTable,
  onDeselect,
}: Props) {
  const translate = useTranslation();
  const label = useLabel();
  const [width, setWidth] = useState(initialWidth);
  const drag = useRef<{
    x: number;
    width: number;
  } | null>(null);
  const { paletteVisible, update } = useSettings();
  const hidden = !paletteVisible;
  const handleResizeStart = (event: PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { x: event.clientX, width };
  };
  const handleResizeMove = (event: PointerEvent<HTMLDivElement>) => {
    const from = drag.current;
    if (from === null) return;
    setWidth(clampWidth(from.width - (event.clientX - from.x)));
  };
  const handleResizeEnd = () => {
    if (drag.current === null) return;
    drag.current = null;
    localStorage.setItem(WIDTH_KEY, String(width));
  };
  return (
    <aside
      className={hidden ? "palette hidden" : "palette"}
      style={hidden ? undefined : { width }}
      onMouseDown={onDeselect}
    >
      {!hidden && (
        <div
          className="palette-resizer"
          title={translate("resizePalette")}
          onPointerDown={handleResizeStart}
          onPointerMove={handleResizeMove}
          onPointerUp={handleResizeEnd}
          onPointerCancel={handleResizeEnd}
        />
      )}

      <div className="palette-head">
        <button
          type="button"
          className="palette-toggle"
          title={translate(hidden ? "showPalette" : "hidePalette")}
          aria-expanded={!hidden}
          onClick={() => update({ paletteVisible: !paletteVisible })}
        >
          {hidden ? "«" : "»"}
        </button>
      </div>

      {!hidden && (
        <div className="palette-body">
          <div className="section-title">{translate("tables")}</div>
          {session.tables.size === 0 && (
            <div className="empty-hint">{translate("noTables")}</div>
          )}
          {[...session.tables].map(([id, spec]) => (
            <div
              className="table-row"
              key={id}
              draggable
              onDragStart={(event) => {
                event.dataTransfer.setData(TABLE_MIME, String(id));
                event.dataTransfer.effectAllowed = "copy";
              }}
            >
              <span className="table-glyph">▦</span>
              <button
                type="button"
                className="table-name"
                data-testid={`table-${id}`}
                title={translate("addScanTooltip")}
                onClick={() => onAddScan(id)}
              >
                {String(spec.name ?? id)}
              </button>
              <button
                type="button"
                className="row-remove"
                onClick={() => onRemoveTable(id)}
              >
                ×
              </button>
            </div>
          ))}

          {categories().map((category) => (
            <div key={category}>
              <div className="section-title">
                {label(`category.${category}`, humanize(category))}
              </div>
              {chipsIn(category).map((chip) => {
                const description = label(`opDesc.${chip.key}`, "");
                return (
                  <div
                    className="op-item"
                    key={chip.key}
                    data-testid={`chip-${chip.key}`}
                    draggable
                    title={description}
                    onDragStart={(event) => {
                      event.dataTransfer.setData(CHIP_MIME, chip.key);
                      event.dataTransfer.effectAllowed = "copy";
                    }}
                  >
                    <span className="op-symbol">{chip.symbol}</span>
                    <span className="op-name">
                      {label(`op.${chip.key}`, humanize(chip.key))}
                    </span>
                    {description.length > 0 && (
                      <span className="op-desc">{description}</span>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      )}
    </aside>
  );
}
