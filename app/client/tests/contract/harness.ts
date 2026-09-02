import { type ChildProcess, spawn, spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const CLIENT_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const REPO_DIR = resolve(CLIENT_DIR, "../..");
const LIBS_DIR = join(REPO_DIR, "app", "server", "build", "libs");
const READY = /PORTA:\s*(\S+)/;
const BOOT_TIMEOUT_MS = 90_000;

export type Backend = { base: string; stop: () => void };

export function installFetchBase(base: string): void {
  const origin = globalThis.fetch;
  globalThis.fetch = ((input: string | URL | Request, init?: RequestInit) => {
    const path = typeof input === "string" ? input : String(input);
    return origin(path.startsWith("/") ? `${base}${path}` : path, init);
  }) as typeof fetch;
}

function findJava(): string {
  const home = process.env.JAVA_HOME;
  if (home !== undefined && existsSync(join(home, "bin", "java")))
    return join(home, "bin", "java");
  return "java";
}

function buildJar(): string {
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const built = spawnSync(
    gradle,
    [":app:server:bundledJar", "-q", "--console=plain"],
    {
      cwd: REPO_DIR,
      encoding: "utf8",
      timeout: 600_000,
    },
  );
  if (built.status !== 0)
    throw new Error(
      `gradle :app:server:bundledJar failed:\n${built.stdout ?? ""}${built.stderr ?? ""}`,
    );
  const jar = readdirSync(LIBS_DIR)
    .filter((name) => /^dbest.*\.jar$/.test(name))
    .sort()
    .at(-1);
  if (jar === undefined) throw new Error(`no dbest*.jar in ${LIBS_DIR}`);
  return join(LIBS_DIR, jar);
}

function locateJar(): string {
  const provided = process.env.DBEST_JAR;
  if (provided === undefined) return buildJar();
  if (!existsSync(provided))
    throw new Error(`DBEST_JAR does not exist: ${provided}`);
  return provided;
}

function waitForReady(child: ChildProcess): Promise<string> {
  return new Promise((resolvePort, reject) => {
    let log = "";
    const timer = setTimeout(
      () =>
        reject(
          new Error(
            `backend never printed "PORTA:" in ${BOOT_TIMEOUT_MS}ms:\n${log}`,
          ),
        ),
      BOOT_TIMEOUT_MS,
    );
    const watch = (chunk: Buffer) => {
      log += String(chunk);
      const match = log.match(READY);
      if (match) {
        clearTimeout(timer);
        resolvePort(match[1]);
      }
    };
    child.stdout?.on("data", watch);
    child.stderr?.on("data", watch);
    child.on("exit", (code) => {
      clearTimeout(timer);
      reject(new Error(`backend exited early (code ${code}):\n${log}`));
    });
  });
}

export async function startBackend(): Promise<Backend> {
  const jar = locateJar();
  const home = mkdtempSync(join(tmpdir(), "dbest-contract-"));
  const sessions = join(home, "sessions");
  mkdirSync(join(home, ".dbest"), { recursive: true });
  mkdirSync(sessions, { recursive: true });
  writeFileSync(
    join(home, ".dbest", "config.json"),
    JSON.stringify({ sessionsDir: sessions }),
  );

  const child = spawn(findJava(), [`-Duser.home=${home}`, "-jar", jar], {
    cwd: REPO_DIR,
    env: { ...process.env, HOME: home, PORT: "0" },
    detached: true,
    stdio: ["ignore", "pipe", "pipe"],
  });
  const base = await waitForReady(child);

  return {
    base,
    stop: () => {
      try {
        if (child.pid !== undefined) process.kill(-child.pid, "SIGTERM");
      } catch {}
      rmSync(home, { recursive: true, force: true });
    },
  };
}
