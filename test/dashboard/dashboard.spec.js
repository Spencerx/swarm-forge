const { test, expect } = require("@playwright/test");
const { spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const repoRoot = path.resolve(__dirname, "../..");
const packWeb = path.join(repoRoot, "swarmforge/scripts/pack_web.sh");

function writeFile(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
}

function seedProject(project) {
  writeFile(
    path.join(project, ".swarmforge/roles.tsv"),
    `specifier\tmaster\t${project}\tspecifier\tSpecifier\tcodex\ttask\n` +
      `coder\tcoder\t${project}/.worktrees/coder\tcoder\tCoder\tcodex\ttask\n`
  );
  writeFile(
    path.join(project, ".swarmforge/board/tasks.tsv"),
    "HTW\tspecifier\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t20260101T000000Z-htw\t0\n"
  );
  writeFile(path.join(project, ".swarmforge/board/HTW.txt"), "Integrate the cave.\n");
  writeFile(path.join(project, "tasks/HTW.md"), "# HTW\n\nIntegrate the cave.\n");
  writeFile(path.join(project, "features/console.feature"), "Feature: console\n");
  writeFile(
    path.join(project, ".swarmforge/handoffs/pending_approval/50_hello.handoff"),
    "from: specifier\n" +
      "to: coder\n" +
      "type: git_handoff\n" +
      "task_id: 20260101T000000Z-htw\n" +
      "task: HTW\n" +
      "artifacts: features/console.feature,tasks/HTW.md\n" +
      "\n" +
      "payload\n"
  );
  writeFile(
    path.join(project, ".swarmforge/dashboard/clarifications/pending/clar-1.request"),
    "id: clar-1\n" +
      "status: pending\n" +
      "role: specifier\n" +
      "created_at: 2026-01-01T00:00:00Z\n" +
      "\n" +
      "Does the bat drop to any of 20 rooms?\n"
  );
}

function seedForge(root) {
  writeFile(
    path.join(root, "packs/four-pack/swarmforge/swarmforge.conf"),
    "window specifier grok master\nwindow coder grok coder\n"
  );
  writeFile(path.join(root, "packs/four-pack/swarmforge/roles/specifier.prompt"), "spec\n");
  writeFile(path.join(root, "packs/four-pack/swarmforge/roles/coder.prompt"), "coder\n");
  fs.mkdirSync(path.join(root, "projects"), { recursive: true });
  const project = path.join(root, "projects/htw");
  seedProject(project);
  writeFile(path.join(root, ".swarmforge/open-projects"), "htw\n");
  writeFile(
    path.join(root, ".swarmforge/roles.tsv"),
    `lieutenant\tmaster\t${root}\tswarmforge-lieutenant\tLieutenant\tgrok\ttask\tforward-only\n`
  );
}

async function startDashboard() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "swarmforge-dashboard."));
  seedForge(root);
  const child = spawn(packWeb, ["--serve", root, "0"], {
    cwd: repoRoot,
    stdio: ["ignore", "pipe", "pipe"]
  });
  const url = await new Promise((resolve, reject) => {
    let buf = "";
    const timer = setTimeout(() => reject(new Error("pack_web --serve timed out")), 10000);
    child.stdout.on("data", (chunk) => {
      buf += chunk.toString();
      const line = buf.split("\n").find((item) => item.startsWith("http://"));
      if (line) {
        clearTimeout(timer);
        resolve(line.trim());
      }
    });
    child.on("error", reject);
    child.on("exit", (code) => {
      clearTimeout(timer);
      reject(new Error("pack_web exited " + code + " " + buf));
    });
  });
  return { root, child, url };
}

async function stopDashboard(handle) {
  if (handle && handle.child && !handle.child.killed) {
    handle.child.kill("SIGTERM");
    await new Promise((resolve) => handle.child.once("exit", resolve));
  }
  if (handle && handle.root) {
    fs.rmSync(handle.root, { recursive: true, force: true });
  }
}

test.describe("pack dashboard", () => {
  let handle;

  test.beforeAll(async () => {
    handle = await startDashboard();
  });

  test.afterAll(async () => {
    await stopDashboard(handle);
  });

  test("places Teardown with the pack title and New Task in the actions", async ({ page }) => {
    await page.goto(handle.url);
    await expect(page.locator(".pack-identity #teardown-btn")).toBeVisible();
    await expect(page.locator(".pack-actions #btn-new-project")).toBeVisible();
    await expect(page.locator(".pack-actions #btn-open-project")).toBeVisible();
    await expect(page.locator(".pack-identity #teardown-btn")).toBeVisible();
    await expect(page.locator(".pack-actions #btn-new-task")).toHaveCount(1);
    await expect(page.locator(".project-header button", { hasText: "New Task" })).toBeVisible();
    await expect(page.locator(".board-toolbar")).toHaveCount(0);
  });

  test("New Task focuses the name field", async ({ page }) => {
    await page.goto(handle.url);
    await page.locator(".project-header button", { hasText: "New Task" }).click();
    await expect(page.locator("#nt-name")).toBeFocused();
  });

  test("Attention lists approvals and clarifications", async ({ page }) => {
    await page.goto(handle.url);
    await expect(page.locator("#attention-approvals .att-row")).toContainText("HTW");
    await expect(page.locator("#attention-approvals .att-row")).toContainText("Approve");
    await expect(page.locator("#attention-approvals .att-row")).toContainText("Reject");
    await expect(page.locator("#attention-approvals .att-row")).toContainText("Documents");
    await expect(page.locator("#attention-clarifications .att-row")).toContainText(
      "Clarification requested from: specifier"
    );
    await expect(page.locator("#attention-clarifications .att-row")).toContainText(
      "Does the bat drop to any of 20 rooms?"
    );
  });

  test("Approve is disabled when a document has comments", async ({ page }) => {
    writeFile(
      path.join(handle.root, "projects/htw/.swarmforge/handoffs/pending_approval/50_hello.reviews.json"),
      JSON.stringify({ "features/console.feature": "use an RNG" })
    );
    await page.goto(handle.url);
    await expect(page.locator("#attention-approvals .btn-approve")).toBeDisabled();
    fs.unlinkSync(path.join(handle.root, "projects/htw/.swarmforge/handoffs/pending_approval/50_hello.reviews.json"));
  });

  test("Documents fetch /doc?path= into a window with Save and Cancel", async ({ page, context }) => {
    await page.goto(handle.url);
    await page.locator("#attention-approvals .menu > button").click();
    const popupPromise = context.waitForEvent("page");
    await page.locator("#attention-approvals .menu-list button", { hasText: "console.feature" }).click();
    const doc = await popupPromise;
    await doc.waitForLoadState("domcontentloaded");
    await expect(doc.locator("pre")).toContainText("Feature: console");
    await expect(doc.locator("#doc-history")).toBeVisible();
    await expect(doc.locator("#doc-history")).toHaveClass(/empty/);
    const histBox = await doc.locator("#doc-history").boundingBox();
    const bodyBox = await doc.locator("#doc-body").boundingBox();
    expect(histBox.height).toBeLessThan(bodyBox.height);
    expect(histBox.height).toBeLessThan(80);
    const split = doc.locator("#doc-split-body");
    const splitBox = await split.boundingBox();
    await doc.mouse.move(splitBox.x + splitBox.width / 2, splitBox.y + splitBox.height / 2);
    await doc.mouse.down();
    await doc.mouse.move(splitBox.x + splitBox.width / 2, splitBox.y - 80, { steps: 5 });
    await doc.mouse.up();
    const afterHist = await doc.locator("#doc-history").boundingBox();
    const afterBody = await doc.locator("#doc-body").boundingBox();
    expect(afterHist.height).toBeGreaterThan(histBox.height);
    expect(afterBody.height).toBeLessThan(bodyBox.height);
    await expect(doc.locator("#doc-diff")).toBeDisabled();
    await expect(doc.locator("#doc-comments")).toBeVisible();
    await expect(doc.locator("#doc-save")).toHaveText("Save");
    await expect(doc.locator("#doc-cancel")).toHaveText("Cancel");
    await doc.locator("#doc-comments").fill("needs an RNG");
    await doc.locator("#doc-cancel").click();
    await expect(doc.isClosed()).toBeTruthy();
    const reviewsPath = path.join(handle.root, "projects/htw/.swarmforge/handoffs/pending_approval/50_hello.reviews.json");
    expect(fs.existsSync(reviewsPath)).toBeFalsy();

    await page.locator("#attention-approvals .menu > button").click();
    const savedPromise = context.waitForEvent("page");
    await page.locator("#attention-approvals .menu-list button", { hasText: "console.feature" }).click();
    const saved = await savedPromise;
    await saved.waitForLoadState("domcontentloaded");
    await saved.locator("#doc-comments").fill("needs an RNG");
    await saved.locator("#doc-save").click();
    await expect.poll(() => fs.existsSync(reviewsPath)).toBeTruthy();
    const reviews = JSON.parse(fs.readFileSync(reviewsPath, "utf8"));
    expect(reviews["features/console.feature"]).toBe("needs an RNG");
    await page.reload();
    await expect(page.locator("#attention-approvals .btn-approve")).toBeDisabled();
    await expect(page.locator("#attention-approvals .doc-mark-bad")).toHaveCount(1);
    const historyPath = path.join(
      handle.root, "projects/htw",
      ".swarmforge/rejected-tasks/20260101T000000Z-htw/reviews.json"
    );
    await expect.poll(() => fs.existsSync(historyPath)).toBeTruthy();
    const history = JSON.parse(fs.readFileSync(historyPath, "utf8"));
    expect(history["features/console.feature"][0].text).toBe("needs an RNG");
    fs.unlinkSync(reviewsPath);
  });

  test("Reject opens the retry dialog", async ({ page }) => {
    await page.goto(handle.url);
    await page.locator("#attention-approvals button", { hasText: "Reject" }).click();
    await expect(page.locator("#reject-layer")).toHaveClass(/open/);
    await expect(page.locator("#rt-title")).toHaveText("HTW");
    await expect(page.locator("#rt-retry")).toHaveText("Retry");
    await expect(page.locator("#rt-accept")).toHaveText("Accept Unchanged");
    await expect(page.locator("#rt-delete")).toHaveText("Delete");
  });

  test("retry dialog comments appear in document history", async ({ page, context }) => {
    const local = await startDashboard();
    try {
      await page.goto(local.url);
      await page.locator("#attention-approvals .menu > button").click();
      const popupPromise = context.waitForEvent("page");
      await page.locator("#attention-approvals .menu-list button", { hasText: "console.feature" }).click();
      const doc = await popupPromise;
      await doc.waitForLoadState("domcontentloaded");
      await doc.locator("#doc-comments").fill("needs an RNG");
      await doc.locator("#doc-save").click();
      await page.locator("#attention-approvals button", { hasText: "Reject" }).click();
      await page.locator("#rt-text").fill("dialog note");
      await page.locator("#rt-retry").click();
      await expect(page.locator("#attention-approvals .att-row")).toHaveCount(0);
      writeFile(
        path.join(local.root, "projects/htw/.swarmforge/handoffs/pending_approval/50_hello.handoff"),
        "from: specifier\n" +
          "to: coder\n" +
          "type: git_handoff\n" +
          "task_id: 20260101T000000Z-htw\n" +
          "task: HTW\n" +
          "artifacts: features/console.feature,tasks/HTW.md\n" +
          "\n" +
          "payload\n"
      );
      await page.reload();
      await page.locator("#attention-approvals .menu > button").click();
      const againPromise = context.waitForEvent("page");
      await page.locator("#attention-approvals .menu-list button", { hasText: "console.feature" }).click();
      const again = await againPromise;
      await again.waitForLoadState("domcontentloaded");
      await expect(again.locator("#doc-history")).toContainText("needs an RNG");
      await expect(again.locator("#doc-history")).toContainText("dialog note");
      await expect(again.locator("#doc-history .hist-sep")).toHaveCount(2);
    } finally {
      await stopDashboard(local);
    }
  });

  test("Clarification Open posts the answer", async ({ page, context }) => {
    const local = await startDashboard();
    try {
      await page.goto(local.url);
      const popupPromise = context.waitForEvent("page");
      await page.locator("#attention-clarifications button", { hasText: "Open" }).click();
      const clar = await popupPromise;
      await clar.waitForLoadState("domcontentloaded");
      await expect(clar.locator("#clar-request")).toContainText("Does the bat drop to any of 20 rooms?");
      await clar.locator("#clar-response").fill("Yes, any of the 20 rooms.");
      await clar.locator("#clar-ok").click();
      await expect(page.locator("#attention-clarifications .att-row")).toHaveCount(0);
    } finally {
      await stopDashboard(local);
    }
  });
});
