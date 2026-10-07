import { humanize, useLabel, useTranslation } from "../i18n";
import { readLiteral, showLiteral } from "../server/literals";
import type { Condition } from "../server/types";
import {
  CONDITION_SHAPES,
  OPERANDS,
  changeShape,
  newComparison,
  operandOf,
  withOperand,
  type Operand,
} from "./conditions";
import { Combobox } from "./Combobox";

type Props = {
  condition: Condition;
  columns: string[];
  operators: string[];
  onChange: (condition: Condition) => void;
  onRemove?: () => void;
};

export function ConditionEditor({
  condition,
  columns,
  operators,
  onChange,
  onRemove,
}: Props) {
  const label = useLabel();
  const translate = useTranslation();
  const shape = condition["@type"];

  return (
    <div className="condition">
      <div className="condition-head">
        <select
          data-testid="condition-shape"
          value={shape}
          onChange={(event) =>
            onChange(
              changeShape(event.target.value as Condition["@type"], condition),
            )
          }
        >
          {CONDITION_SHAPES.map((option) => (
            <option key={option} value={option}>
              {label(`shape.${option}`, humanize(option))}
            </option>
          ))}
        </select>

        {condition["@type"] === "cmp" && (
          <>
            <Combobox
              value={condition.left}
              columns={columns}
              placeholder="u.age"
              onChange={(text) => onChange({ ...condition, left: text.trim() })}
            />
            <select
              data-testid="condition-op"
              value={condition.op}
              onChange={(event) =>
                onChange({ ...condition, op: event.target.value })
              }
            >
              {operators.map((option) => (
                <option key={option} value={option}>
                  {label(`enum.${option}`, option)}
                </option>
              ))}
            </select>
            <select
              data-testid="condition-operand"
              value={operandOf(condition.right)}
              onChange={(event) =>
                onChange({
                  ...condition,
                  right: withOperand(
                    condition.right,
                    event.target.value as Operand,
                  ),
                })
              }
            >
              {OPERANDS.map((operand) => (
                <option key={operand} value={operand}>
                  {translate(`operand.${operand}`)}
                </option>
              ))}
            </select>
            {"ref" in condition.right ? (
              <Combobox
                value={condition.right.ref}
                columns={columns}
                placeholder="o.id"
                onChange={(text) =>
                  onChange({ ...condition, right: { ref: text.trim() } })
                }
              />
            ) : (
              <input
                type="text"
                data-testid="condition-right"
                value={showLiteral(condition.right)}
                placeholder="18"
                onChange={(event) =>
                  onChange({
                    ...condition,
                    right: readLiteral(event.target.value),
                  })
                }
              />
            )}
          </>
        )}
        {(condition["@type"] === "isNull" ||
          condition["@type"] === "isNotNull") && (
          <Combobox
            value={condition.column}
            columns={columns}
            placeholder="u.age"
            onChange={(text) => onChange({ ...condition, column: text.trim() })}
          />
        )}
        {onRemove !== undefined && (
          <button type="button" className="row-remove" onClick={onRemove}>
            ×
          </button>
        )}
      </div>

      {(condition["@type"] === "and" || condition["@type"] === "or") && (
        <div className="condition-children">
          {condition.conditions.map((child, index) => (
            <ConditionEditor
              key={index}
              condition={child}
              columns={columns}
              operators={operators}
              onChange={(next) =>
                onChange({
                  ...condition,
                  conditions: condition.conditions.map((old, at) =>
                    at === index ? next : old,
                  ),
                })
              }
              onRemove={() =>
                onChange({
                  ...condition,
                  conditions: condition.conditions.filter(
                    (_child, at) => at !== index,
                  ),
                })
              }
            />
          ))}
          <button
            type="button"
            className="btn-add"
            data-testid="condition-add"
            onClick={() =>
              onChange({
                ...condition,
                conditions: [...condition.conditions, newComparison()],
              })
            }
          >
            {translate("addCondition")}
          </button>
        </div>
      )}
    </div>
  );
}
