// Fonction d'envoi des notifications iPhone (Supabase Edge Function, appelée chaque minute par pg_cron).
// Secrets attendus : VAPID_PUBLIC, VAPID_PRIVATE, CRON_SECRET (SUPABASE_URL et SUPABASE_SERVICE_ROLE_KEY sont fournis par Supabase).
import webpush from "npm:web-push@3.6.7";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const NAMES: Record<string, [string, string]> = {
  FAJR: ["Fajr", "الفجر"], DHUHR: ["Dhuhr", "الظهر"], ASR: ["Asr", "العصر"], MAGHRIB: ["Maghrib", "المغرب"], ISHA: ["Isha", "العشاء"],
};

Deno.serve(async (req) => {
  if (req.headers.get("x-cron-secret") !== Deno.env.get("CRON_SECRET")) return new Response("interdit", { status: 403 });
  webpush.setVapidDetails("https://ilyassbg09-glitch.github.io/salatlock", Deno.env.get("VAPID_PUBLIC")!, Deno.env.get("VAPID_PRIVATE")!);
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await sb.rpc("push_due");
  if (error) return new Response("erreur : " + error.message, { status: 500 });
  let sent = 0, gone = 0;
  for (const row of data ?? []) {
    const [fr, ar] = NAMES[row.prayer] ?? [row.prayer, ""];
    const payload = JSON.stringify({
      title: `🕌 C'est l'heure de ${fr} · ${ar}`,
      body: row.grace > 0 ? `Tes applis se bloquent dans ${row.grace} min. Va faire tes ablutions 🚰` : "Va faire tes ablutions et prier 🚰",
      url: "./?adhan=1",
    });
    try {
      await webpush.sendNotification({ endpoint: row.endpoint, keys: { p256dh: row.p256dh, auth: row.auth } }, payload, { TTL: 900, urgency: "high" });
      sent++;
    } catch (e) {
      const code = (e as { statusCode?: number }).statusCode;
      if (code === 404 || code === 410) { await sb.rpc("push_unsubscribe", { p_endpoint: row.endpoint }); gone++; }
    }
  }
  return new Response(JSON.stringify({ sent, gone }), { headers: { "Content-Type": "application/json" } });
});
