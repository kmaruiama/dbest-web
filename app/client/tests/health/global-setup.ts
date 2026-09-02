import { writeFileSync } from "node:fs";
import { STATE_FILE, makeTestHome, sessionsDirOf } from "./env";
import { prepareHome, startBackend, startVite } from "./server";

export default async function globalSetup(): Promise<void> {
  const home = makeTestHome();
  prepareHome(home);
  console.log(`[health] test home ${home}`);
  console.log(`[health] sessions  ${sessionsDirOf(home)}`);
  const backend = await startBackend(home);
  console.log(`[health] backend up (pid ${backend.pid})`);
  const vite = await startVite();
  console.log(`[health] vite up (pid ${vite.pid})`);
  writeFileSync(
    STATE_FILE,
    JSON.stringify({ home, backend: backend.pid, vite: vite.pid }),
  );
  backend.unref();
  vite.unref();
}
