import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SECTIONS, sidebarShape } from "../src/commons/lib/sections";

// The shape at each width, shared with the Android app: spec/layout.md, its cases in spec/fixtures/layout.json.
const spec = JSON.parse(readFileSync(join(import.meta.dir, "../../../spec/fixtures/layout.json"), "utf8"));
const source = (path: string) => readFileSync(join(import.meta.dir, "../src", path), "utf8");

interface Shape {
  sidebar: "none" | "rail" | "labels";
  columns: 1 | 2;
}

const expected = (c: { expect: Shape; desktop?: Shape }): Shape => c.desktop ?? c.expect;

/** A Tailwind length in px, `w-[228px]` or a spacing step (`px-7` is 7 × 4 px), found in a file's classes. */
function px(text: string, pattern: RegExp): number {
  const m = text.match(pattern);
  if (!m) throw new Error(`${pattern} is not in the source`);
  return m[1].endsWith("px") ? Number.parseInt(m[1], 10) : Number(m[1]) * 4;
}

// The columns are CSS (`@container` on each page), so they are read from the classes that draw them: the page's
// width is the window's less the sidebar, its hairline and the page's padding on both sides.
const sidebar = source("commons/components/Sidebar.tsx");
const LABELS = px(sidebar, /"w-\[(\d+px)\] px-/);
const RAIL = px(sidebar, /"w-(\d+) items-center/);
const PADDING = px(source("App.tsx"), /<main key=\{section\} className=\{`[^`]*\bpx-(\d+)\b/);
const TWO_COLUMNS_PAGE = (() => {
  const all = ["modules/home/HomeScreen.tsx", "modules/connect/ConnectScreen.tsx", "modules/versions/VersionsScreen.tsx"]
    .flatMap((path) => [...source(path).matchAll(/@min-\[(\d+)px\]:grid-cols/g)].map((m) => Number(m[1])));
  // Connect has a step before it (the code beside the address, under the switch); the page's two columns are the last.
  expect(all.filter((n) => n === Math.max(...all)).length).toBeGreaterThanOrEqual(3);
  return Math.max(...all);
})();

function columnsAt(width: number): 1 | 2 {
  const side = sidebarShape(width) === "rail" ? RAIL : LABELS;
  return width - side - 1 - 2 * PADDING >= TWO_COLUMNS_PAGE ? 2 : 1;
}

test("the sections, in the spec's order, with only Technical Logs set apart", () => {
  expect(SECTIONS.map(({ section, apart }) => ({ section, apart }))).toEqual(spec.sections);
});

test("the sidebar's shape at each width is the spec's", () => {
  for (const c of spec.widths) {
    const sidebar: string = sidebarShape(c.width);
    expect({ width: c.width, sidebar }).toEqual({ width: c.width, sidebar: expected(c).sidebar });
  }
});

test("the pages' columns at each width are the spec's", () => {
  expect(LABELS).toBe(228);
  for (const c of spec.widths) {
    const columns: number = columnsAt(c.width);
    expect({ width: c.width, columns }).toEqual({ width: c.width, columns: expected(c).columns });
  }
});

test("a page keeps the spec's readable width, Settings its own", () => {
  expect(source("commons/components/Page.tsx")).toContain(`max-w-[${spec.readable.page}px]`);
  expect(source("modules/settings/SettingsScreen.tsx")).toContain(`max-w-[${spec.readable.settings}px]`);
});

test("the sidebar's labels start at the spec's width", () => {
  expect(sidebarShape(spec.sidebar_from - 1)).toBe("rail");
  expect(sidebarShape(spec.sidebar_from)).toBe("labels");
});
