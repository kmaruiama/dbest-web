import { createContext, useContext, type ReactNode } from "react";
import type { Api } from "../server/api";
import type { Catalog } from "../server/types";

type Connection = { api: Api; catalog: Catalog };

const ConnectionContext = createContext<Connection | null>(null);

type Props = Connection & { children: ReactNode };

export function ConnectionProvider({ api, catalog, children }: Props) {
  return (
    <ConnectionContext.Provider value={{ api, catalog }}>
      {children}
    </ConnectionContext.Provider>
  );
}

export function useApi(): Api {
  return useConnection().api;
}

export function useCatalog(): Catalog {
  return useConnection().catalog;
}

function useConnection(): Connection {
  const value = useContext(ConnectionContext);
  if (value === null) throw new Error("used before the server answered");
  return value;
}
