import { existsSync, readFileSync, rmSync } from "node:fs";
import { STATE_FILE } from "./env";
import { stopTree } from "./server";

export default async function globalTeardown(): Promise<void> {
  if (!existsSync(STATE_FILE)) return;
  const state = JSON.parse(readFileSync(STATE_FILE, "utf8")) as {
    home: string;
    backend?: number;
    vite?: number;
  };
  stopTree(state.vite);
  stopTree(state.backend);
  await new Promise((done) => setTimeout(done, 500));
  rmSync(state.home, { recursive: true, force: true });
  rmSync(STATE_FILE, { force: true });
  console.log("[health] torn down; test home removed");
}
