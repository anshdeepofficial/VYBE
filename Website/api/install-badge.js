import { command } from "./_kv.js";

export const config = { runtime: "edge" };

export default async function handler() {
  let count = "unavailable";
  try { count = String(Number(await command("SCARD", "vybe:installations")) || 0); } catch (_) {}
  const width = count === "unavailable" ? 182 : 142;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="20" role="img" aria-label="VYBE installs: ${count}"><linearGradient id="s"><stop stop-color="#555"/><stop offset="1" stop-color="#333"/></linearGradient><rect width="${width}" height="20" rx="3" fill="url(#s)"/><rect x="82" width="${width - 82}" height="20" rx="3" fill="#8b45d6"/><text x="41" y="14" fill="#fff" text-anchor="middle" font-family="Verdana" font-size="11">VYBE installs</text><text x="${82 + (width - 82) / 2}" y="14" fill="#fff" text-anchor="middle" font-family="Verdana" font-size="11">${count}</text></svg>`;
  return new Response(svg, { headers: { "content-type": "image/svg+xml", "cache-control": "public, max-age=300" } });
}
