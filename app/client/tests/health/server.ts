import { spawn, spawnSync, type ChildProcess } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import {
  BACKEND_DIR,
  BACKEND_PORT,
  BACKEND_URL,
  REPOSITORY_DIR,
  VITE_PORT,
  configFileOf,
  sessionsDirOf,
} from "./env";

const READY_LINE = "PORTA:";
const BOOT_TIMEOUT_MS = 90000;
const MAIN_CLASS = "dbest.kernel.http.ServerKt";
const ANSI = /\x1b\[[0-9;]*m/g;

function stripAnsi(text: string): string {
  return text.replace(ANSI, "");
}

function findJava(): string {
  const ambient = process.env.JAVA_HOME;
  if (ambient !== undefined && existsSync(join(ambient, "bin", "java")))
    return join(ambient, "bin", "java");
  return "java";
}

export function prepareHome(home: string): void {
  mkdirSync(join(home, ".dbest"), { recursive: true });
  mkdirSync(sessionsDirOf(home), { recursive: true });
  writeFileSync(
    configFileOf(home),
    JSON.stringify({ sessionsDir: sessionsDirOf(home) }),
  );
}

function buildBackend(): string {
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const built = spawnSync(
    gradle,
    [":app:server:installDist", "-q", "--console=plain"],
    {
      cwd: REPOSITORY_DIR,
      env: process.env,
      encoding: "utf8",
      timeout: 600000,
    },
  );
  if (built.status !== 0) {
    throw new Error(
      `gradle installDist failed:\n${built.stdout ?? ""}${built.stderr ?? ""}`,
    );
  }
  return join(BACKEND_DIR, "build", "install", "dbest", "lib", "*");
}

function waitForLine(
  child: ChildProcess,
  needle: string,
  what: string,
): Promise<void> {
  return new Promise((resolve, reject) => {
    let log = "";
    const timer = setTimeout(
      () =>
        reject(
          new Error(
            `${what} never printed "${needle}" in ${BOOT_TIMEOUT_MS}ms:\n${log}`,
          ),
        ),
      BOOT_TIMEOUT_MS,
    );
    const settle = () => {
      clearTimeout(timer);
      resolve();
    };
    const watch = (chunk: Buffer) => {
      log += stripAnsi(String(chunk));
      if (log.includes(needle)) settle();
    };
    child.stdout?.on("data", watch);
    child.stderr?.on("data", watch);
    child.on("exit", (code) => {
      clearTimeout(timer);
      reject(new Error(`${what} exited early (code ${code}):\n${log}`));
    });
  });
}

export async function startBackend(home: string): Promise<ChildProcess> {
  const java = findJava();
  const classpath = buildBackend();
  const child = spawn(
    java,
    [`-Duser.home=${home}`, "-cp", classpath, MAIN_CLASS],
    {
      cwd: BACKEND_DIR,
      env: { ...process.env, HOME: home, PORT: String(BACKEND_PORT) },
      detached: true,
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  await waitForLine(child, READY_LINE, "backend");
  return child;
}

export async function startVite(): Promise<ChildProcess> {
  const child = spawn(
    "npx",
    [
      "vite",
      "--port",
      String(VITE_PORT),
      "--strictPort",
      "--host",
      "127.0.0.1",
    ],
    {
      cwd: process.cwd(),
      env: { ...process.env, DBEST_API_URL: BACKEND_URL },
      detached: true,
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  await waitForLine(child, `:${VITE_PORT}`, "vite");
  return child;
}

export function stopTree(pid: number | undefined): void {
  if (pid === undefined) return;
  try {
    process.kill(-pid, "SIGTERM");
  } catch {
    try {
      process.kill(pid, "SIGTERM");
    } catch {}
  }
}
