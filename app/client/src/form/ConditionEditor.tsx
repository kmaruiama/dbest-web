import { humanize, useLabel, useTranslation } from "../i18n";
import {
  readColumnRef,
  readLiteral,
  showColumnRef,
  showLiteral,
} from "../server/literals";
import type {
  ComparisonCondition,
  Condition,
  GroupCondition,
  NullCheckCondition,
} from "../server/types";
import {
  appendChild,
  changeShape,
  CONDITION_SHAPES,
  conditionShape,
  removeChild,
  replaceChild,
  withComparisonColumn,
  withComparisonOperator,
  withComparisonValue,
  withNullColumn,
} from "./conditions";
import { Combobox } from "./Combobox";

type Props = {
  condition: Condition;
  columns: string[];
  operators: string[];
  onChange: (condition: Condition) => void;
  onRemove?: () => void;
};

const labelKey = (shape: string) => {
  if (shape === "comparison") return "cmp";
  if (shape === "nullCheck") return "isNull";
  if (shape === "notNull") return "isNotNull";
  return shape === "all" ? "and" : "or";
};

export function ConditionEditor({
  condition,
  columns,
  operators,
  onChange,
  onRemove,
}: Props) {
  const label = useLabel();
  const shape = conditionShape(condition);

  return (
    <div className="condition">
      <div className="condition-head">
        <select
          data-testid="condition-shape"
          value={shape}
          onChange={(event) =>
            onChange(changeShape(event.target.value as typeof shape, condition))
          }
        >
          {CONDITION_SHAPES.map((option) => (
            <option key={option} value={option}>
              {label(`shape.${labelKey(option)}`, humanize(option))}
            </option>
          ))}
        </select>

        {condition.kind === "comparison" && (
          <ComparisonEditor
            condition={condition}
            columns={columns}
            operators={operators}
            onChange={onChange}
          />
        )}
        {condition.kind === "nullCheck" && (
          <NullCheckEditor
            condition={condition}
            columns={columns}
            onChange={onChange}
          />
        )}
        {onRemove !== undefined && (
          <button type="button" className="row-remove" onClick={onRemove}>
            ×
          </button>
        )}
      </div>

      {condition.kind === "group" && (
        <GroupEditor
          condition={condition}
          columns={columns}
          operators={operators}
          onChange={onChange}
        />
      )}
    </div>
  );
}

function ComparisonEditor({
  condition,
  columns,
  operators,
  onChange,
}: {
  condition: ComparisonCondition;
  columns: string[];
  operators: string[];
  onChange: (condition: Condition) => void;
}) {
  const label = useLabel();

  return (
    <>
      <Combobox
        value={showColumnRef(condition.left)}
        columns={columns}
        placeholder="u.age"
        onChange={(text) =>
          onChange(withComparisonColumn(condition, readColumnRef(text)))
        }
      />
      <select
        data-testid="condition-op"
        value={condition.operator}
        onChange={(event) =>
          onChange(withComparisonOperator(condition, event.target.value))
        }
      >
        {operators.map((option) => (
          <option key={option} value={option}>
            {label(`enum.${option}`, option)}
          </option>
        ))}
      </select>
      <input
        type="text"
        data-testid="condition-right"
        value={showLiteral(condition.value)}
        placeholder="18"
        onChange={(event) =>
          onChange(
            withComparisonValue(condition, readLiteral(event.target.value)),
          )
        }
      />
    </>
  );
}

function NullCheckEditor({
  condition,
  columns,
  onChange,
}: {
  condition: NullCheckCondition;
  columns: string[];
  onChange: (condition: Condition) => void;
}) {
  return (
    <Combobox
      value={showColumnRef(condition.column)}
      columns={columns}
      placeholder="u.age"
      onChange={(text) =>
        onChange(withNullColumn(condition, readColumnRef(text)))
      }
    />
  );
}

function GroupEditor({
  condition,
  columns,
  operators,
  onChange,
}: {
  condition: GroupCondition;
  columns: string[];
  operators: string[];
  onChange: (condition: Condition) => void;
}) {
  const translate = useTranslation();

  return (
    <div className="condition-children">
      {condition.children.map((child, index) => (
        <ConditionEditor
          key={index}
          condition={child}
          columns={columns}
          operators={operators}
          onChange={(next) => onChange(replaceChild(condition, index, next))}
          onRemove={() => onChange(removeChild(condition, index))}
        />
      ))}
      <button
        type="button"
        className="btn-add"
        data-testid="condition-add"
        onClick={() => onChange(appendChild(condition))}
      >
        {translate("addCondition")}
      </button>
    </div>
  );
}
