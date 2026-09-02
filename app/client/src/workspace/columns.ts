import { api } from "../server/api";
import { showColumnRef } from "../server/literals";
import type { NodeId, Port, Session } from "../server/types";

export type FeedingColumns = {
  all: string[];
  byPort: Record<Port, string[]>;
};

function push(names: string[], qualified: string): void {
  if (!names.includes(qualified)) names.push(qualified);
}

export async function columnsFeeding(
  sid: string,
  session: Session,
  id: NodeId,
): Promise<FeedingColumns> {
  const incoming = session.edges.filter((edge) => edge.to === id);
  const schemas = await Promise.all(
    incoming.map((edge) => api.schema(sid, edge.from)),
  );
  const feeding: FeedingColumns = {
    all: [],
    byPort: { ONLY: [], LEFT: [], RIGHT: [] },
  };
  incoming.forEach((edge, index) => {
    for (const column of schemas[index]) {
      const qualified = showColumnRef(column);
      push(feeding.all, qualified);
      push(feeding.byPort[edge.port], qualified);
    }
  });
  return feeding;
}
