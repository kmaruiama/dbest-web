import { useState } from "react";
import { field, objectField, type FormValue } from "./FormDraft";
import { newComparison } from "./conditions";
import { humanize, useLabel, useTranslation } from "../i18n";
import {
  chipOf,
  fieldsOf,
  inputPorts,
  variantOptions,
} from "../server/catalog";
import type { Condition, FieldSpec, PlanNode, Port } from "../server/types";
import type { FeedingColumns } from "../workspace/columns";
import { ConditionEditor } from "./ConditionEditor";
import { Field } from "./Field";
import {
  blankRow,
  itemsAt,
  withAppended,
  withCell,
  withField,
  withItem,
  withoutItem,
} from "./values";

const PORT_AT: Record<string, Port> = { left: "LEFT", right: "RIGHT" };

type Props = {
  node: PlanNode;
  columns: FeedingColumns;
  error: string | null;
  busy: boolean;
  onSubmit: (node: PlanNode) => void;
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
  const [draft, setDraft] = useState(node);
  const type = node.kind;
  const chip = chipOf(node);
  const key = chip?.key ?? type;
  const name = label(`op.${key}`, humanize(key));
  const binary = inputPorts(type).length === 2;
  const listAt = (at: string) => {
    const port = binary ? PORT_AT[at] : undefined;
    return port === undefined ? columns.all : columns.byPort[port];
  };
  const controlsFor = (spec: FieldSpec, item: FormValue, index: number) => {
    if (spec.widget === "list") {
      return (
        <Field
          spec={{ ...spec, widget: spec.item ?? "text" }}
          value={item}
          columns={columns.all}
          legal={null}
          onChange={(value) =>
            value !== undefined &&
            setDraft(withItem(draft, spec.at, index, value))
          }
        />
      );
    }
    return (spec.of ?? []).map((cell) => (
      <Field
        key={cell.at}
        spec={cell}
        value={objectField(item)[cell.at]}
        columns={listAt(cell.at)}
        legal={null}
        onChange={(value) =>
          setDraft(withCell(draft, spec.at, index, cell.at, value))
        }
      />
    ));
  };
  const bodyFor = (spec: FieldSpec) => {
    if (spec.widget === "condition") {
      return (
        <ConditionEditor
          condition={conditionAt(field(draft.fields, spec.at))}
          columns={columns.all}
          operators={spec.options ?? []}
          onChange={(next) => setDraft(withField(draft, spec.at, next))}
        />
      );
    }
    if (spec.widget === "list" || spec.widget === "rows") {
      return (
        <>
          {itemsAt(draft, spec.at).map((item, index) => (
            <div className="row" data-testid="row" key={index}>
              {controlsFor(spec, item, index)}
              <button
                type="button"
                className="row-remove"
                onClick={() => setDraft(withoutItem(draft, spec.at, index))}
              >
                ×
              </button>
            </div>
          ))}
          <button
            type="button"
            className="btn-add"
            data-testid="add-row"
            onClick={() =>
              setDraft(withAppended(draft, spec.at, blankRow(spec)))
            }
          >
            {translate("addRow")}
          </button>
        </>
      );
    }
    return (
      <Field
        spec={spec}
        value={field(draft.fields, spec.at)}
        columns={columns.all}
        legal={variantOptions(draft, spec.at)}
        onChange={(value) => setDraft(withField(draft, spec.at, value))}
      />
    );
  };
  const renderField = (spec: FieldSpec) => (
    <div className="field" data-testid={`field-${spec.at}`} key={spec.at}>
      <label>{label(`field.${type}.${spec.at}`, humanize(spec.at))}</label>
      {bodyFor(spec)}
    </div>
  );
  return (
    <div className="overlay" onMouseDown={onCancel}>
      <div
        className="dialog form"
        data-testid="form"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2>{translate("editTitle", { op: name })}</h2>
        {fieldsOf(type).map(renderField)}
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
      </div>
    </div>
  );
}

function conditionAt(value: FormValue | undefined): Condition {
  if (isCondition(value)) return value;
  return newComparison();
}

function isCondition(value: FormValue | undefined): value is Condition {
  if (typeof value !== "object" || value === null || !("kind" in value))
    return false;
  if (value.kind === "comparison")
    return "left" in value && "operator" in value && "value" in value;
  if (value.kind === "nullCheck")
    return "column" in value && "negated" in value;
  return value.kind === "group" && "operator" in value && "children" in value;
}
