import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const target = process.env.DBEST_API_URL ?? "http://localhost:8000";

const apiPaths = [
  "/bootstrap",
  "/config",
  "/files",
  "/operators",
  "/sessions",
  "/pick-file",
  "/csv-preview",
  "/xml-preview",
  "/shutdown",
];

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5274,
    proxy: Object.fromEntries(
      apiPaths.map((path) => [path, { target, changeOrigin: true }]),
    ),
  },
});
