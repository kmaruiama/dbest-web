import type {
  Edge,
  NodeId,
  PlanNode,
  Position,
  TableId,
} from "../server/types";
import type { TableMode } from "../tables/TableModal";
import type { FeedingColumns } from "./columns";

export type Interaction =
  | {
      kind: "idle";
    }
  | {
      kind: "namingAlias";
      table: TableId;
      at: Position;
    }
  | {
      kind: "renamingAlias";
      id: NodeId;
      current: string;
    }
  | {
      kind: "linking";
      from: NodeId;
    }
  | {
      kind: "choosing";
      options: Edge[];
    }
  | {
      kind: "editing";
      id: NodeId;
      node: PlanNode;
      columns: FeedingColumns;
    }
  | {
      kind: "running";
      id: NodeId;
    }
  | {
      kind: "newTable";
      mode: TableMode;
    };

export type InteractionState = {
  interaction: Interaction;
  notice: string | null;
};

const IDLE: Interaction = { kind: "idle" };

export const INITIAL: InteractionState = { interaction: IDLE, notice: null };

export type InteractionAction =
  | {
      kind: "reset";
    }
  | {
      kind: "startLink";
      from: NodeId;
    }
  | {
      kind: "choose";
      options: Edge[];
    }
  | {
      kind: "nameAlias";
      table: TableId;
      at: Position;
    }
  | {
      kind: "renameAlias";
      id: NodeId;
      current: string;
    }
  | {
      kind: "edit";
      id: NodeId;
      node: PlanNode;
      columns: FeedingColumns;
    }
  | {
      kind: "run";
      id: NodeId;
    }
  | {
      kind: "newTable";
      mode: TableMode;
    }
  | {
      kind: "notify";
      message: string;
    }
  | {
      kind: "clearNotice";
    };

export function reduce(
  state: InteractionState,
  action: InteractionAction,
): InteractionState {
  switch (action.kind) {
    case "reset":
      return state.interaction.kind === "idle" && state.notice === null
        ? state
        : INITIAL;
    case "startLink":
      return {
        interaction: { kind: "linking", from: action.from },
        notice: state.notice,
      };
    case "choose":
      return {
        interaction: { kind: "choosing", options: action.options },
        notice: state.notice,
      };
    case "nameAlias":
      return {
        interaction: {
          kind: "namingAlias",
          table: action.table,
          at: action.at,
        },
        notice: state.notice,
      };
    case "renameAlias":
      return {
        interaction: {
          kind: "renamingAlias",
          id: action.id,
          current: action.current,
        },
        notice: state.notice,
      };
    case "edit":
      return {
        interaction: {
          kind: "editing",
          id: action.id,
          node: action.node,
          columns: action.columns,
        },
        notice: state.notice,
      };
    case "run":
      return {
        interaction: { kind: "running", id: action.id },
        notice: state.notice,
      };
    case "newTable":
      return {
        interaction: { kind: "newTable", mode: action.mode },
        notice: state.notice,
      };
    case "notify":
      return { interaction: IDLE, notice: action.message };
    case "clearNotice":
      return state.notice === null
        ? state
        : { interaction: state.interaction, notice: null };
  }
}
