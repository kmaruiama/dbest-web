import { useState } from "react";
import { isCondition, newComparison } from "./conditions";
import { humanize, useLabel, useTranslation } from "../i18n";
import {
  chipOf,
  fieldsOf,
  inputPorts,
  variantOptions,
} from "../server/catalog";
import type {
  Columns,
  FieldSpec,
  FormValue,
  Node,
  Port,
  Widget,
} from "../server/types";
import { Dialog } from "../ui/Dialog";
import { useCatalog } from "../ui/connection";
import { ConditionEditor } from "./ConditionEditor";
import { Field } from "./Field";
import { offeredColumns } from "./offer";
import {
  blankRow,
  itemsAt,
  withAppended,
  withCell,
  withField,
  withItem,
  withoutItem,
  withoutPlaceholders,
} from "./values";

const PORT_AT: Record<string, Port> = { left: "LEFT", right: "RIGHT" };

type Props = {
  node: Node;
  columns: Columns;
  error: string | null;
  busy: boolean;
  onSubmit: (node: Node) => void;
  onCancel: () => void;
};

export function Form({
  node,
  columns,
  error,
  busy,
  onSubmit,
  onCancel,
}: Props) {
  const translate = useTranslation();
  const label = useLabel();
  const catalog = useCatalog();
  const [draft, setDraft] = useState(() =>
    withoutPlaceholders(
      node,
      fieldsOf(catalog, node["@type"]),
      catalog.placeholder,
    ),
  );
  const type = node["@type"];
  const chip = chipOf(catalog, node);
  const key = chip?.key ?? type;
  const name = label(`op.${key}`, humanize(key));
  const ports = inputPorts(catalog, type);
  const offer = (widget: Widget, at?: string) =>
    offeredColumns(
      columns,
      widget,
      ports.length > 0,
      ports.length === 2 && at !== undefined ? PORT_AT[at] : undefined,
    );
  return (
    <Dialog className="form" testId="form" onClose={onCancel}>
      <h2>{translate("editTitle", { op: name })}</h2>
      {fieldsOf(catalog, type).map((spec) => (
        <FormField
          key={spec.at}
          spec={spec}
          type={type}
          draft={draft}
          offer={offer}
          onChange={setDraft}
        />
      ))}
      {error !== null && (
        <div className="form-error" data-testid="form-error">
          {error}
        </div>
      )}
      <div className="dialog-actions">
        <button
          type="button"
          className="btn-ghost"
          data-testid="form-cancel"
          onClick={onCancel}
        >
          {translate("cancel")}
        </button>
        <button
          type="button"
          className="btn"
          data-testid="form-save"
          disabled={busy}
          onClick={() => onSubmit(draft)}
        >
          {translate("save")}
        </button>
      </div>
    </Dialog>
  );
}

type FormFieldProps = {
  spec: FieldSpec;
  type: string;
  draft: Node;
  offer: (widget: Widget, at?: string) => string[];
  onChange: (node: Node) => void;
};

function FormField({ spec, type, draft, offer, onChange }: FormFieldProps) {
  const label = useLabel();
  return (
    <div className="field" data-testid={`field-${spec.at}`}>
      <label>{label(`field.${type}.${spec.at}`, humanize(spec.at))}</label>
      <FieldBody spec={spec} draft={draft} offer={offer} onChange={onChange} />
    </div>
  );
}

type FieldBodyProps = Omit<FormFieldProps, "type">;

function FieldBody({ spec, draft, offer, onChange }: FieldBodyProps) {
  const translate = useTranslation();
  const catalog = useCatalog();
  if (spec.widget === "condition") {
    const current = draft[spec.at];
    return (
      <ConditionEditor
        condition={isCondition(current) ? current : newComparison()}
        columns={offer(spec.widget)}
        operators={spec.options}
        onChange={(next) => onChange(withField(draft, spec.at, next))}
      />
    );
  }
  if (spec.widget === "list" || spec.widget === "rows") {
    return (
      <>
        {itemsAt(draft, spec.at).map((item, index) => (
          <div className="row" data-testid="row" key={index}>
            <RowControls
              spec={spec}
              item={item}
              index={index}
              draft={draft}
              offer={offer}
              onChange={onChange}
            />
            <button
              type="button"
              className="row-remove"
              onClick={() => onChange(withoutItem(draft, spec.at, index))}
            >
              ×
            </button>
          </div>
        ))}
        <button
          type="button"
          className="btn-add"
          data-testid="add-row"
          onClick={() => onChange(withAppended(draft, spec.at, blankRow(spec)))}
        >
          {translate("addRow")}
        </button>
      </>
    );
  }
  return (
    <Field
      spec={spec}
      value={draft[spec.at]}
      columns={offer(spec.widget)}
      legal={variantOptions(catalog, draft, spec.at)}
      onChange={(value) => onChange(withField(draft, spec.at, value))}
    />
  );
}

type RowControlsProps = FieldBodyProps & {
  item: FormValue;
  index: number;
};

function RowControls({
  spec,
  item,
  index,
  draft,
  offer,
  onChange,
}: RowControlsProps) {
  if (spec.widget === "list") {
    return (
      <Field
        spec={{ ...spec, widget: spec.item ?? "text" }}
        value={item}
        columns={offer(spec.widget)}
        legal={null}
        onChange={(value) =>
          value !== undefined &&
          onChange(withItem(draft, spec.at, index, value))
        }
      />
    );
  }
  return (spec.of ?? []).map((cell) => (
    <Field
      key={cell.at}
      spec={cell}
      value={cellOf(item, cell.at)}
      columns={offer(cell.widget, cell.at)}
      legal={null}
      onChange={(value) =>
        onChange(withCell(draft, spec.at, index, cell.at, value))
      }
    />
  ));
}

function cellOf(row: FormValue, at: string): FormValue | undefined {
  return row !== null && typeof row === "object" && !Array.isArray(row)
    ? row[at]
    : undefined;
}
