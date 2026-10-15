// Runs every journey check in turn and reports which failed. Each check also runs on its own (see package.json).
import { spawnSync } from "node:child_process";

const checks = ["layout", "keyboard", "enquiries", "booking", "privatetrip", "accounts", "translation", "reviews"];
const failed = checks.filter(name => {
  console.log(`\n== ${name}`);
  return spawnSync(process.execPath, [new URL(`./${name}.mjs`, import.meta.url).pathname], { stdio: "inherit" }).status !== 0;
});
console.log(failed.length ? `\nFailed: ${failed.join(", ")}` : "\nAll journey checks passed");
process.exitCode = failed.length ? 1 : 0;
