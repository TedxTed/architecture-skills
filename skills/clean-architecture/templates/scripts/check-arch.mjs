// 依賴方向檢查（TypeScript / JavaScript 專案用，不需安裝任何套件）
// 用法：複製到專案的 scripts/check-arch.mjs，調整下方 CONFIG，加入 package.json：
//   "check:arch": "node scripts/check-arch.mjs"
// 其他語言請用 languages/*.md 中列出的工具（import-linter、depguard、ArchUnit）。

import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

const CONFIG = {
  // 掃描的根目錄（全端專案改成 "server/src"）
  root: "src",
  // 內層禁止 import 的套件（依專案的框架、ORM、SDK 調整）
  forbiddenPackages: [/^express$/, /^@nestjs\//, /^vue$/, /^react$/, /^pinia$/, /^@prisma\//, /^typeorm$/, /^csv-/, /^node:/],
  // by-feature 的模組資料夾（by-layer 專案設為 null）；模組之間只能 import 對方的 public-api
  modulesDir: "src/modules",
  publicApiFile: /public-api\.(ts|js)$/,
};

// 每一層禁止 import 的「層」
const LAYER_RULES = {
  domain: ["application", "adapters", "infrastructure"],
  application: ["adapters", "infrastructure"],
};

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    if (name === "node_modules") return [];
    return statSync(full).isDirectory() ? walk(full) : /\.(ts|tsx|js|mjs|vue)$/.test(name) ? [full] : [];
  });
}

const toPosix = (p) => p.split(path.sep).join("/");
const layerOf = (file) => toPosix(file).split("/").find((seg) => seg in LAYER_RULES || seg === "adapters" || seg === "infrastructure");
const moduleOf = (file) => {
  if (!CONFIG.modulesDir) return null;
  const rel = toPosix(path.relative(CONFIG.modulesDir, file));
  return rel.startsWith("..") ? null : rel.split("/")[0];
};

const violations = [];

for (const file of walk(CONFIG.root)) {
  const layer = layerOf(file);
  const text = readFileSync(file, "utf-8");
  const specs = [...text.matchAll(/(?:from|import)\s*\(?\s*["']([^"']+)["']/g)].map((m) => m[1]);

  for (const spec of specs) {
    const isRelative = spec.startsWith(".");
    const target = isRelative ? path.resolve(path.dirname(file), spec) : null;

    // 1. 內層不得 import 外層
    if (layer in LAYER_RULES) {
      if (target && LAYER_RULES[layer].includes(layerOf(target))) {
        violations.push(`${file}：${layer} 不得 import ${layerOf(target)}（"${spec}"）`);
      }
      if (!isRelative && CONFIG.forbiddenPackages.some((re) => re.test(spec))) {
        violations.push(`${file}：${layer} 不得 import 套件 "${spec}"`);
      }
    }

    // 2. 模組之間只能透過 public-api
    if (target && CONFIG.modulesDir) {
      const from = moduleOf(file);
      const to = moduleOf(target);
      if (from && to && from !== to && !CONFIG.publicApiFile.test(toPosix(target) + ".ts") && !CONFIG.publicApiFile.test(toPosix(target))) {
        violations.push(`${file}：模組 ${from} 只能透過 public-api 使用模組 ${to}（"${spec}"）`);
      }
    }
  }
}

if (violations.length > 0) {
  violations.forEach((v) => console.log("違反：" + v));
  console.log(`共 ${violations.length} 處違反依賴方向`);
  process.exit(1);
}
console.log("依賴方向檢查通過");
