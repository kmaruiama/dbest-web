import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const BACKEND_PORT = Number(process.env.HEALTH_BACKEND_PORT ?? 8931);

export const VITE_PORT = Number(process.env.HEALTH_VITE_PORT ?? 5931);

export const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;

export const APP_URL = `http://localhost:${VITE_PORT}`;

export const CLIENT_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../..",
);

export const REPOSITORY_DIR = resolve(CLIENT_DIR, "../..");

export const BACKEND_DIR = join(REPOSITORY_DIR, "app", "server");

export const STATE_FILE = join(tmpdir(), "dbest-health-state.json");

export function makeTestHome(): string {
  return mkdtempSync(join(tmpdir(), "dbest-health-"));
}

export function sessionsDirOf(home: string): string {
  return join(home, "sessions");
}

export function configFileOf(home: string): string {
  return join(home, ".dbest", "config.json");
}
