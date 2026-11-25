// Writes the Video Downloader icon as Android VectorDrawables, from measurements of diseño/imagenes/18-2-icono.png
// (1254 px there = the 72 dp visible area of a 108 dp adaptive icon). An SVG preview with the same path data can be
// written for checking the drawing in a browser.
// Usage, from codigo/: node scripts/generar-icono.mjs [svg preview file]
import { writeFileSync } from "node:fs";

const RES = new URL("../app/src/main/res/", import.meta.url);
const PX = 72 / 1254; // dp per image pixel

const WHITE = "#FFFFFF";
const TURQUOISE = "#48D6C6";
const VIOLET = "#5C5CFF";
const DEEP_TURQUOISE = "#14A295"; // turquoise that still shows on the light background

// Measured in image pixels, relative to the ring centre (625.4, 610.6), which becomes the icon centre.
const RING = { r: 379.3, stroke: 86.1 };
// Cap-inclusive extents in degrees, clockwise from 12 o'clock, mirrored and with even 6° gaps. `accent` marks the
// turquoise segment at the bottom.
const SEGMENTS = [
  { from: -33.85, to: 33.85 },
  { from: 39.85, to: 103.65 },
  { from: 109.65, to: 147.95 },
  { from: 153.95, to: 206.05, accent: true },
  { from: 212.05, to: 250.35 },
  { from: 256.35, to: 320.15 },
];
const SHAFT = { halfWidth: 70.5, top: 368 - 610.6, radius: 40, bottom: 570 - 610.6 + 20 };
const HEAD = { halfWidth: 222, top: 570 - 610.6, tip: 789 - 610.6, cornerRadius: 30.3, tipRadius: 43 };
const BAR = { halfWidth: 180, top: 793 - 610.6, bottom: 855 - 610.6 };

// Background: diagonal violet gradient plus a lavender glow at the top-left of the visible area (108 dp layer).
const LINEAR = { start: [18, 18], end: [69.34, 102.59], stops: [[0, "#6C2FFF"], [0.5, "#3F1ADA"], [1, "#12048C"]] };
const GLOW = { center: [18, 18], radius: 40, color: "#C8A0FF", alpha: 0.61 };

const num = (n) => {
  const v = Math.round(n * 1000) / 1000;
  return Object.is(v, -0) || v === 0 ? "0" : String(v);
};
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
const mul = (a, k) => [a[0] * k, a[1] * k];
const unit = (a) => mul(a, 1 / Math.hypot(a[0], a[1]));
const argb = (hex, alpha = 1) => "#" + Math.round(alpha * 255).toString(16).padStart(2, "0").toUpperCase() + hex.slice(1);

// Closed polygon with optionally rounded corners; points run clockwise on screen.
function roundedPolygon(points, radii) {
  const corners = points.map((p, i) => {
    const prev = points[(i - 1 + points.length) % points.length];
    const next = points[(i + 1) % points.length];
    const toPrev = unit(sub(prev, p));
    const toNext = unit(sub(next, p));
    const angle = Math.acos(toPrev[0] * toNext[0] + toPrev[1] * toNext[1]);
    const t = radii[i] / Math.tan(angle / 2);
    return { in: add(p, mul(toPrev, t)), out: add(p, mul(toNext, t)), r: radii[i] };
  });
  let d = `M${num(corners[0].out[0])},${num(corners[0].out[1])}`;
  for (let j = 1; j <= corners.length; j++) {
    const c = corners[j % corners.length];
    d += ` L${num(c.in[0])},${num(c.in[1])}`;
    if (c.r > 0) d += ` A${num(c.r)},${num(c.r)} 0 0 1 ${num(c.out[0])},${num(c.out[1])}`;
  }
  return `${d} Z`;
}

const circle = (cx, cy, r) => `M${num(cx - r)},${num(cy)} A${num(r)},${num(r)} 0 1 1 ${num(cx + r)},${num(cy)} A${num(r)},${num(r)} 0 1 1 ${num(cx - r)},${num(cy)} Z`;

// Geometry of the symbol at scale k (1 = adaptive icon) centred on (cx, cy).
function symbol(k, cx, cy) {
  const s = PX * k;
  const at = (x, y) => [cx + x * s, cy + y * s];
  const radius = RING.r * s;
  const stroke = RING.stroke * s;
  const capDegrees = (Math.asin(stroke / 2 / radius) * 180) / Math.PI;
  const onRing = (deg) => {
    const a = (deg * Math.PI) / 180;
    return [cx + radius * Math.sin(a), cy - radius * Math.cos(a)];
  };
  const arc = (seg) => {
    const a0 = seg.from + capDegrees;
    const a1 = seg.to - capDegrees;
    const [x0, y0] = onRing(a0);
    const [x1, y1] = onRing(a1);
    return `M${num(x0)},${num(y0)} A${num(radius)},${num(radius)} 0 ${a1 - a0 > 180 ? 1 : 0} 1 ${num(x1)},${num(y1)}`;
  };
  const shaft = roundedPolygon(
    [at(-SHAFT.halfWidth, SHAFT.top), at(SHAFT.halfWidth, SHAFT.top), at(SHAFT.halfWidth, SHAFT.bottom), at(-SHAFT.halfWidth, SHAFT.bottom)],
    [SHAFT.radius * s, SHAFT.radius * s, 0, 0],
  );
  const head = roundedPolygon(
    [at(-HEAD.halfWidth, HEAD.top), at(HEAD.halfWidth, HEAD.top), at(0, HEAD.tip)],
    [HEAD.cornerRadius * s, HEAD.cornerRadius * s, HEAD.tipRadius * s],
  );
  const barRadius = ((BAR.bottom - BAR.top) / 2) * s;
  const bar = roundedPolygon(
    [at(-BAR.halfWidth, BAR.top), at(BAR.halfWidth, BAR.top), at(BAR.halfWidth, BAR.bottom), at(-BAR.halfWidth, BAR.bottom)],
    [barRadius, barRadius, barRadius, barRadius],
  );
  return {
    stroke,
    ring: SEGMENTS.filter((seg) => !seg.accent).map(arc).join(" "),
    accentArc: SEGMENTS.filter((seg) => seg.accent).map(arc).join(" "),
    arrow: `${shaft} ${head}`,
    bar,
  };
}

const header = (size, viewport, aapt = false) =>
  `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/generar-icono.mjs. Edit the script, not this file. -->\n` +
  `<vector xmlns:android="http://schemas.android.com/apk/res/android"${aapt ? `\n    xmlns:aapt="http://schemas.android.com/aapt"` : ""}\n` +
  `    android:width="${size}dp"\n    android:height="${size}dp"\n    android:viewportWidth="${viewport}"\n    android:viewportHeight="${viewport}">\n`;

function symbolPaths(sym, main, accent, indent = "    ") {
  const stroked = (d, color) =>
    `${indent}<path\n${indent}    android:pathData="${d}"\n${indent}    android:strokeWidth="${num(sym.stroke)}"\n` +
    `${indent}    android:strokeColor="${color}"\n${indent}    android:strokeLineCap="round" />\n`;
  const filled = (d, color) => `${indent}<path\n${indent}    android:pathData="${d}"\n${indent}    android:fillColor="${color}" />\n`;
  return stroked(sym.ring, main) + stroked(sym.accentArc, accent) + filled(sym.arrow, main) + filled(sym.bar, accent);
}

const SQUARE = "M0,0 L108,0 L108,108 L0,108 Z";
function gradientPaths(indent = "    ") {
  const items = LINEAR.stops.map(([o, c]) => `${indent}            <item\n${indent}                android:color="${argb(c)}"\n${indent}                android:offset="${o}" />`).join("\n");
  return (
    `${indent}<path android:pathData="${SQUARE}">\n${indent}    <aapt:attr name="android:fillColor">\n` +
    `${indent}        <gradient\n${indent}            android:endX="${LINEAR.end[0]}"\n${indent}            android:endY="${LINEAR.end[1]}"\n` +
    `${indent}            android:startX="${LINEAR.start[0]}"\n${indent}            android:startY="${LINEAR.start[1]}"\n` +
    `${indent}            android:type="linear">\n${items}\n${indent}        </gradient>\n${indent}    </aapt:attr>\n${indent}</path>\n` +
    `${indent}<path android:pathData="${SQUARE}">\n${indent}    <aapt:attr name="android:fillColor">\n` +
    `${indent}        <gradient\n${indent}            android:centerX="${GLOW.center[0]}"\n${indent}            android:centerY="${GLOW.center[1]}"\n` +
    `${indent}            android:endColor="${argb(GLOW.color, 0)}"\n${indent}            android:gradientRadius="${GLOW.radius}"\n` +
    `${indent}            android:startColor="${argb(GLOW.color, GLOW.alpha)}"\n${indent}            android:type="radial" />\n` +
    `${indent}    </aapt:attr>\n${indent}</path>\n`
  );
}

const solid = (color) => header(108, 108) + `    <path\n        android:fillColor="${color}"\n        android:pathData="${SQUARE}" />\n</vector>\n`;

// The visible 72 dp square, clipped to a rounded square or a circle, for places that are not adaptive icons.
function logo(clip) {
  return (
    header(48, 72, true) +
    `    <clip-path android:pathData="${clip}" />\n    <group\n        android:translateX="-18"\n        android:translateY="-18">\n` +
    gradientPaths("        ") +
    symbolPaths(symbol(1, 54, 54), WHITE, TURQUOISE, "        ") +
    "    </group>\n</vector>\n"
  );
}

const adaptive = symbol(1, 54, 54);
const roundedSquare = roundedPolygon([[0, 0], [72, 0], [72, 72], [0, 72]], [16, 16, 16, 16]);
const files = {
  "drawable/ic_launcher_background.xml": header(108, 108, true) + gradientPaths() + "</vector>\n",
  "drawable/ic_launcher_background_dark.xml": solid("#0D0E14"),
  "drawable/ic_launcher_background_light.xml": solid("#F6F6FA"),
  "drawable/ic_launcher_foreground.xml": header(108, 108) + symbolPaths(adaptive, WHITE, TURQUOISE) + "</vector>\n",
  "drawable/ic_launcher_foreground_light.xml": header(108, 108) + symbolPaths(adaptive, VIOLET, DEEP_TURQUOISE) + "</vector>\n",
  // Themed icons: Android only uses the shape and paints it with the wallpaper colors.
  "drawable/ic_launcher_monochrome.xml": header(108, 108) + symbolPaths(adaptive, WHITE, WHITE) + "</vector>\n",
  // Notification icon: the symbol alone, 22 dp across inside 24 dp.
  "drawable/ic_launcher_foreground_large.xml": header(24, 24) + symbolPaths(symbol(11 / 24.25, 12, 12), WHITE, WHITE) + "</vector>\n",
  "drawable/ic_app_icon.xml": logo(roundedSquare),
  // Android 7 has no adaptive icons; it shows these as they are.
  "mipmap-anydpi/ic_launcher.xml": logo(roundedSquare),
  "mipmap-anydpi/ic_launcher_round.xml": logo(circle(36, 36, 36)),
};
for (const [path, text] of Object.entries(files)) writeFileSync(new URL(path, RES), text);
console.log(`Escritos: ${Object.keys(files).join(", ")}`);

const preview = process.argv[2];
if (preview) {
  const svgSymbol = (sym, main, accent) =>
    `<path d="${sym.ring}" fill="none" stroke="${main}" stroke-width="${num(sym.stroke)}" stroke-linecap="round"/>` +
    `<path d="${sym.accentArc}" fill="none" stroke="${accent}" stroke-width="${num(sym.stroke)}" stroke-linecap="round"/>` +
    `<path d="${sym.arrow}" fill="${main}"/><path d="${sym.bar}" fill="${accent}"/>`;
  const background = `<rect width="108" height="108" fill="url(#lin)"/><rect width="108" height="108" fill="url(#glow)"/>`;
  const panel = (x, clip, body) => `<g transform="translate(${x},-40)"><g clip-path="url(#${clip})">${body}</g></g>`;
  const notification = symbol(11 / 24.25, 12, 12);
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="1350" height="300" viewBox="0 0 450 100" style="background:#0D0E14">` +
    `<defs><linearGradient id="lin" gradientUnits="userSpaceOnUse" x1="${LINEAR.start[0]}" y1="${LINEAR.start[1]}" x2="${LINEAR.end[0]}" y2="${LINEAR.end[1]}">` +
    LINEAR.stops.map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`).join("") +
    `</linearGradient><radialGradient id="glow" gradientUnits="userSpaceOnUse" cx="${GLOW.center[0]}" cy="${GLOW.center[1]}" r="${GLOW.radius}">` +
    `<stop offset="0" stop-color="${GLOW.color}" stop-opacity="${GLOW.alpha}"/><stop offset="1" stop-color="${GLOW.color}" stop-opacity="0"/></radialGradient>` +
    `<clipPath id="square"><rect x="18" y="18" width="72" height="72"/></clipPath><clipPath id="circle"><circle cx="54" cy="54" r="36"/></clipPath></defs>` +
    panel(-14, "square", background + svgSymbol(adaptive, WHITE, TURQUOISE)) +
    panel(66, "circle", background + svgSymbol(adaptive, WHITE, TURQUOISE)) +
    panel(146, "circle", `<rect width="108" height="108" fill="#F6F6FA"/>` + svgSymbol(adaptive, VIOLET, DEEP_TURQUOISE)) +
    `<g transform="translate(226,-40)"><circle cx="54" cy="54" r="36" fill="#DCE1FF"/>${svgSymbol(adaptive, "#2A3375", "#2A3375")}</g>` +
    `<rect x="340" y="30" width="104" height="40" fill="#000"/>` +
    `<g transform="translate(348,38)">${svgSymbol(notification, WHITE, WHITE)}</g>` +
    `<g transform="translate(382,44) scale(0.5)">${svgSymbol(notification, WHITE, WHITE)}</g>` +
    `</svg>`;
  writeFileSync(preview, svg);
  console.log(`Vista previa: ${preview}`);
}
