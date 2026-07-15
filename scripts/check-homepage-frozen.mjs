// Frozen-homepage guard (ADR-001): the approved homepage must never change.
// Compares SHA-256 hashes of every frozen file against the committed manifest.
//
//   node scripts/check-homepage-frozen.mjs           → verify (CI + pre-merge)
//   node scripts/check-homepage-frozen.mjs --update  → regenerate manifest
//     (ONLY with explicit owner approval — the homepage design is final)
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const manifestPath = join(repoRoot, "scripts", "homepage-freeze.manifest.json");

const FROZEN_FILES = [
  "apps/web/src/app/page.tsx",
  "apps/web/src/app/globals.css",
  "apps/web/src/data/home.ts",
];
const FROZEN_DIRS = ["apps/web/src/components/home", "apps/web/public/images/wedjan"];

function walk(dir) {
  const entries = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      entries.push(...walk(full));
    } else {
      entries.push(full);
    }
  }
  return entries;
}

function collectFrozenFiles() {
  const files = FROZEN_FILES.map((f) => join(repoRoot, f));
  for (const dir of FROZEN_DIRS) {
    files.push(...walk(join(repoRoot, dir)));
  }
  return files.sort();
}

function hashFile(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function buildManifest() {
  const manifest = {};
  for (const file of collectFrozenFiles()) {
    manifest[relative(repoRoot, file).replaceAll("\\", "/")] = hashFile(file);
  }
  return manifest;
}

const current = buildManifest();

if (process.argv.includes("--update")) {
  writeFileSync(manifestPath, JSON.stringify(current, null, 2) + "\n");
  console.log(`Homepage freeze manifest updated (${Object.keys(current).length} files).`);
  process.exit(0);
}

let expected;
try {
  expected = JSON.parse(readFileSync(manifestPath, "utf8"));
} catch {
  console.error("✖ Missing scripts/homepage-freeze.manifest.json — run with --update once.");
  process.exit(1);
}

const problems = [];
for (const [file, hash] of Object.entries(expected)) {
  if (!(file in current)) {
    problems.push(`DELETED: ${file}`);
  } else if (current[file] !== hash) {
    problems.push(`MODIFIED: ${file}`);
  }
}
for (const file of Object.keys(current)) {
  if (!(file in expected)) {
    problems.push(`ADDED (into frozen tree): ${file}`);
  }
}

if (problems.length > 0) {
  console.error("✖ Homepage freeze violated (ADR-001). The homepage design is final.\n");
  for (const problem of problems) console.error("  - " + problem);
  console.error(
    "\nRevert these changes. New features belong in new routes/components, never in the frozen tree.",
  );
  process.exit(1);
}

console.log(`✔ Homepage frozen and intact (${Object.keys(expected).length} files verified).`);
