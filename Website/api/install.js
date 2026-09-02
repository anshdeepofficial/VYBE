import { command, json } from "./_kv.js";

export const config = { runtime: "edge" };

export default async function handler(request) {
  if (!["POST", "DELETE"].includes(request.method)) return json({ error: "method_not_allowed" }, 405);
  try {
    const body = await request.json();
    const installation = String(body.installation || "");
    if (!/^[a-f0-9]{64}$/.test(installation)) return json({ error: "invalid_installation" }, 400);
    if (request.method === "DELETE") {
      await command("SREM", "vybe:installations", installation);
      await command("ZREM", "vybe:active", installation);
      await command("DEL", `vybe:install:${installation}`);
      return json({ ok: true });
    }
    const now = Date.now();
    await command("SADD", "vybe:installations", installation);
    await command("ZADD", "vybe:active", now, installation);
    await command("HSETNX", `vybe:install:${installation}`, "firstSeen", now);
    await command(
      "HSET", `vybe:install:${installation}`,
      "lastSeen", now,
      "version", String(body.version || "unknown").slice(0, 32),
      "versionCode", String(body.versionCode || "unknown").slice(0, 16),
      "sdk", String(body.sdk || "unknown").slice(0, 8),
      "abi", String(body.abi || "unknown").slice(0, 32),
    );
    return json({ ok: true });
  } catch (error) {
    return json({ error: "counter_unavailable", detail: error.message }, 503);
  }
}
