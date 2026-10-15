import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: loadEnv(mode, ".", "").API_PROXY_TARGET ?? "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    globals: true,
    // Form tests type and click through whole journeys; on a busy machine or CI runner they can exceed the 5 s default.
    testTimeout: 15_000
  }
}));
