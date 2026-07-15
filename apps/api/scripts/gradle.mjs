// Cross-platform Gradle wrapper launcher so pnpm/turbo scripts work on
// Windows (gradlew.bat) and POSIX (./gradlew) alike.
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const apiDir = join(dirname(fileURLToPath(import.meta.url)), "..");
const isWindows = process.platform === "win32";
const wrapper = isWindows ? join(apiDir, "gradlew.bat") : join(apiDir, "gradlew");

const result = spawnSync(wrapper, process.argv.slice(2), {
  cwd: apiDir,
  stdio: "inherit",
  shell: isWindows,
});

process.exit(result.status ?? 1);
