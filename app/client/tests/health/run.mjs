#!/usr/bin/env node

import { spawn } from "node:child_process";

const passed = process.argv.slice(2);
const verbose = passed.includes("-v") || passed.includes("--verbose");
const rest = passed.filter((arg) => arg !== "-v" && arg !== "--verbose");

const args = ["playwright", "test", ...rest];
if (verbose) args.push("--headed");

const child = spawn("npx", args, {
  stdio: "inherit",
  env: verbose ? { ...process.env, HEALTH_SLOWMO: "120" } : process.env,
});
child.on("exit", (code, signal) =>
  process.exit(signal !== null ? 1 : (code ?? 1)),
);
