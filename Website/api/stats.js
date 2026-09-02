import { command, json } from "./_kv.js";

export const config = { runtime: "edge" };

export default async function handler() {
  try {
    const total = Number(await command("SCARD", "vybe:installations")) || 0;
    const active30Days = Number(await command("ZCOUNT", "vybe:active", Date.now() - 30 * 86400000, "+inf")) || 0;
    return json({ totalUniqueInstallations: total, active30Days });
  } catch (error) {
    return json({ error: "counter_unavailable", detail: error.message }, 503);
  }
}
