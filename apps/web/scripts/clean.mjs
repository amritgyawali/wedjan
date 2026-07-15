import { rmSync } from "node:fs";

const generatedPaths = [".next", "out", "build", "tsconfig.tsbuildinfo"];

for (const path of generatedPaths) {
  rmSync(path, { recursive: true, force: true });
}

console.log("Removed generated Next.js and TypeScript output.");

