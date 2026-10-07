import { useState, type PointerEvent } from "react";
import { humanize, useLabel, useTranslation } from "../i18n";
import { categories, chipsIn } from "../server/catalog";
import type { Session } from "../server/types";
import { useCatalog } from "../ui/connection";
import { clampWidth, useSettings } from "../ui/settings";
import { CHIP_MIME, TABLE_MIME, type PaletteEvent } from "./canvas";

type Props = {
  session: Session;
  onEvent: (event: PaletteEvent) => void;
};

export function Palette({ session, onEvent }: Props) {
  const translate = useTranslation();
  const label = useLabel();
  const catalog = useCatalog();

  const [resizing, setResizing] = useState<{
    x: number;
    startWidth: number;
    width: number;
  } | null>(null);
  const { paletteVisible, paletteWidth, collapsedCategories, update } =
    useSettings();
  const width = resizing?.width ?? paletteWidth;
  const hidden = !paletteVisible;
  const handleResizeStart = (event: PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setResizing({ x: event.clientX, startWidth: width, width });
  };
  const handleResizeMove = (event: PointerEvent<HTMLDivElement>) => {
    if (resizing === null) return;
    const width = clampWidth(
      resizing.startWidth - (event.clientX - resizing.x),
    );
    setResizing({ ...resizing, width });
  };
  const handleResizeEnd = () => {
    if (resizing === null) return;
    setResizing(null);
    update({ paletteWidth: width });
  };
  const handleToggleCategory = (category: string) => {
    const next = new Set(collapsedCategories);
    if (next.has(category)) next.delete(category);
    else next.add(category);
    update({ collapsedCategories: next });
  };
  return (
    <aside
      className={hidden ? "palette hidden" : "palette"}
      style={hidden ? undefined : { width }}
      onMouseDown={() => onEvent({ kind: "paletteMouseDown" })}
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
          {Object.keys(session.tables).length === 0 && (
            <div className="empty-hint">{translate("noTables")}</div>
          )}
          {Object.entries(session.tables).map(([id, spec]) => (
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
                onClick={() =>
                  onEvent({ kind: "paletteTableClicked", table: Number(id) })
                }
              >
                {String(spec.name ?? id)}
              </button>
              <button
                type="button"
                className="row-remove"
                onClick={() =>
                  onEvent({ kind: "paletteTableRemoved", table: Number(id) })
                }
              >
                ×
              </button>
            </div>
          ))}

          {categories(catalog).map((category) => {
            const collapsed = collapsedCategories.has(category);
            const categoryLabel = label(
              `category.${category}`,
              humanize(category),
            );
            const contentId = `palette-category-${category}`;
            return (
              <section className="palette-section" key={category}>
                <h2 className="section-title">
                  <button
                    type="button"
                    className="section-toggle"
                    aria-expanded={!collapsed}
                    aria-controls={contentId}
                    onClick={() => handleToggleCategory(category)}
                  >
                    <span className="section-chevron" aria-hidden="true">
                      {collapsed ? "▸" : "▾"}
                    </span>
                    {categoryLabel}
                  </button>
                </h2>
                {!collapsed && (
                  <div id={contentId}>
                    {chipsIn(catalog, category).map((chip) => {
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
                )}
              </section>
            );
          })}
        </div>
      )}
    </aside>
  );
}
