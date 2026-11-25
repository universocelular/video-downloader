// Writes the Violet Flow palette as Android color resources for light (values) and dark (values-night) mode.
// Usage, from codigo/: node scripts/generar-colores.mjs
import { writeFileSync } from "node:fs";

const RES = new URL("../app/src/main/res/", import.meta.url);

// Fixed neutrals. Dark follows the chosen design (diseño/imagenes/01-5-hoja-de-estilo.png); light is its counterpart.
const NEUTRALS = {
  dark: {
    background: "#0D0E14", surface_lowest: "#08090D", surface_low: "#13141C", surface: "#191B26",
    surface_high: "#242738", surface_highest: "#2C2F42", surface_variant: "#242738",
    surface_bright: "#2C2F42", surface_dim: "#0D0E14",
    text: "#F5F5FA", text_secondary: "#B9BCCB", outline: "#4A4E66", outline_variant: "#2F3242",
    error: "#FF6B7A", on_error: "#3D0008", error_container: "#4D1A22", on_error_container: "#FFD9DC",
    inverse_surface: "#F5F5FA", inverse_on_surface: "#191B26",
    secondary: "#ADAEDB", on_secondary: "#1C1D3D", switch_track_off: "#3A3D4F",
  },
  light: {
    background: "#F6F6FA", surface_lowest: "#FFFFFF", surface_low: "#FBFBFD", surface: "#FFFFFF",
    surface_high: "#EEEEF5", surface_highest: "#E5E6EE", surface_variant: "#E5E6EE",
    surface_bright: "#FFFFFF", surface_dim: "#DCDDE6",
    text: "#15161D", text_secondary: "#5A5D6E", outline: "#8E91A4", outline_variant: "#DADBE5",
    error: "#C2273A", on_error: "#FFFFFF", error_container: "#FFDADC", on_error_container: "#410009",
    inverse_surface: "#2A2C3B", inverse_on_surface: "#F2F2F8",
    secondary: "#5E6085", on_secondary: "#FFFFFF", switch_track_off: "#C9CBD6",
  },
};

// "Contraste alto": pure background, firmer borders and brighter secondary text.
const HIGH_CONTRAST = {
  dark: {
    background: "#000000", surface_lowest: "#000000", surface_low: "#08090D", surface: "#101118",
    surface_high: "#1A1C26", surface_highest: "#242635", text_secondary: "#D4D6E2",
    outline: "#6E7290", outline_variant: "#3C3F52",
  },
  light: {
    background: "#FFFFFF", surface_lowest: "#FFFFFF", surface_low: "#FAFAFD", surface: "#FFFFFF",
    surface_high: "#F0F0F6", surface_highest: "#E6E7EF", text_secondary: "#3B3D4B",
    outline: "#6A6D80", outline_variant: "#BFC1CF",
  },
};

// The five "Color principal" choices of diseño/imagenes/09-2-apariencia.png. `fill` paints buttons, switches and
// progress; `text` is the readable tone for text and icons on the background (at least 4.5:1).
const SUPPORT = {
  turquoise: { dark: "#48D6C6", onDark: "#00201C", light: "#0B7A70", onLight: "#FFFFFF" },
  violet: { dark: "#9D9DFF", onDark: "#14145A", light: "#5C5CFF", onLight: "#FFFFFF" },
};
const ACCENTS = {
  violet: { fill: "#5C5CFF", onFill: "#FFFFFF", textDark: "#9D9DFF", textLight: "#4747E0", support: "turquoise" },
  turquoise: { fill: "#3CCFC2", onFill: "#00201D", textDark: "#5EE0D2", textLight: "#0B7A70", support: "violet" },
  pink: { fill: "#EC5FA0", onFill: "#3A0021", textDark: "#FF8CC2", textLight: "#B5175F", support: "turquoise" },
  orange: { fill: "#FF8A3D", onFill: "#2E1300", textDark: "#FFAA70", textLight: "#A84A00", support: "turquoise" },
  lime: { fill: "#A6E05A", onFill: "#172300", textDark: "#BDEB80", textLight: "#436D00", support: "violet" },
};

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const hex = (c) => "#" + c.map((v) => Math.round(v).toString(16).padStart(2, "0").toUpperCase()).join("");
const mix = (top, bottom, amount) => hex(rgb(top).map((v, i) => v * amount + rgb(bottom)[i] * (1 - amount)));
const luminance = (h) =>
  rgb(h)
    .map((v) => v / 255)
    .map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
    .reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

function accentColors(mode) {
  const n = NEUTRALS[mode];
  const out = {};
  for (const [name, a] of Object.entries(ACCENTS)) {
    const s = SUPPORT[a.support];
    const support = mode === "dark" ? s.dark : s.light;
    Object.assign(out, {
      [`${name}_primary`]: a.fill,
      [`${name}_on_primary`]: a.onFill,
      [`${name}_container`]: mode === "dark" ? mix(a.fill, n.surface, 0.32) : mix(a.fill, "#FFFFFF", 0.16),
      [`${name}_on_container`]: mode === "dark" ? mix(a.fill, "#FFFFFF", 0.25) : mix(a.fill, "#000000", 0.45),
      [`${name}_text`]: mode === "dark" ? a.textDark : a.textLight,
      [`${name}_inverse`]: mode === "dark" ? a.textLight : a.textDark,
      [`${name}_support`]: support,
      [`${name}_on_support`]: mode === "dark" ? s.onDark : s.onLight,
      [`${name}_support_container`]: mode === "dark" ? mix(support, n.surface, 0.25) : mix(support, "#FFFFFF", 0.18),
      [`${name}_on_support_container`]: mode === "dark" ? mix(support, "#FFFFFF", 0.3) : mix(support, "#000000", 0.55),
    });
    // Fail loudly instead of shipping unreadable text.
    const checks = [
      [`${name} texto`, out[`${name}_text`], n.background, 4.5],
      [`${name} texto en superficie alta`, out[`${name}_text`], n.surface_high, 4.5],
      [`${name} sobre relleno`, a.onFill, a.fill, 4.5],
      [`${name} sobre contenedor`, out[`${name}_on_container`], out[`${name}_container`], 4.5],
    ];
    for (const [what, fg, bg, min] of checks) {
      const c = contrast(fg, bg);
      if (c < min) throw new Error(`${mode}: contraste ${what} ${c.toFixed(2)} < ${min}`);
    }
  }
  return out;
}

function write(mode, folder) {
  const blocks = [
    ["Violet Flow neutrals", NEUTRALS[mode], "vd_"],
    ["High contrast (\"Contraste alto\")", HIGH_CONTRAST[mode], "vd_hc_"],
    ["Accents (\"Color principal\")", accentColors(mode), "vd_"],
  ];
  let xml = `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/generar-colores.mjs (${mode} mode). Edit the script, not this file. -->\n<resources>\n`;
  for (const [title, colors, prefix] of blocks) {
    xml += `\n    <!-- ${title} -->\n`;
    for (const [name, value] of Object.entries(colors)) xml += `    <color name="${prefix}${name}">${value}</color>\n`;
  }
  writeFileSync(new URL(`${folder}/colors_videodownloader.xml`, RES), xml + "</resources>\n");
}

write("light", "values");
write("dark", "values-night");
for (const mode of ["dark", "light"]) {
  const n = NEUTRALS[mode];
  console.log(`${mode}: texto ${contrast(n.text, n.background).toFixed(1)}:1, texto secundario ${contrast(n.text_secondary, n.surface).toFixed(1)}:1`);
}
