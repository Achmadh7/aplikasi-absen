import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface AbsensiRecord {
  id: number;
  nis: string;
  tanggal: string;
  masuk_menit: number | null;
  pulang_menit: number | null;
  wa_masuk_status: string;
  wa_pulang_status: string;
}

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: AbsensiRecord | null;
  old_record: AbsensiRecord | null;
}

function formatJam(menit: number): string {
  const j = Math.floor(menit / 60).toString().padStart(2, "0");
  const m = (menit % 60).toString().padStart(2, "0");
  return `${j}.${m}`;
}

function normalisasiWa(nomor: string): string | null {
  const digit = nomor.replace(/\D/g, "");
  if (digit.length < 9) return null;
  const hasil = digit.startsWith("62")
    ? digit
    : digit.startsWith("0")
    ? `62${digit.slice(1)}`
    : digit.startsWith("8")
    ? `62${digit}`
    : digit;
  return hasil.length >= 10 && hasil.length <= 16 ? hasil : null;
}

function statusMasukLabel(sesi: string, masukMenit: number, toleransi = 15): string {
  const standar = sesi === "SIANG" ? 13 * 60 : 7 * 60;
  return masukMenit <= standar + toleransi ? "Hadir" : "Terlambat";
}

async function kirimViaFonnte(target: string, message: string): Promise<boolean> {
  const token = Deno.env.get("FONNTE_TOKEN");
  if (!token) throw new Error("FONNTE_TOKEN belum di-set");

  const body = new URLSearchParams({ target, message, countryCode: "62" });
  const res = await fetch("https://api.fonnte.com/send", {
    method: "POST",
    headers: { Authorization: token },
    body,
  });
  const json = await res.json().catch(() => ({}));
  return res.ok && json?.status !== false;
}

async function kirimViaMetaCloud(target: string, message: string): Promise<boolean> {
  const token = Deno.env.get("META_WA_TOKEN");
  const phoneNumberId = Deno.env.get("META_WA_PHONE_ID");
  if (!token || !phoneNumberId) throw new Error("META_WA_TOKEN / META_WA_PHONE_ID belum di-set");

  const res = await fetch(`https://graph.facebook.com/v21.0/${phoneNumberId}/messages`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      to: target,
      type: "text",
      text: { body: message },
    }),
  });
  return res.ok;
}

Deno.serve(async (req) => {
  try {
    const secret = Deno.env.get("WEBHOOK_SECRET");
    if (secret && req.headers.get("x-webhook-secret") !== secret) {
      return new Response("Unauthorized", { status: 401 });
    }

    const payload: WebhookPayload = await req.json();
    const rec = payload.record;
    if (payload.table !== "absensi" || !rec || payload.type === "DELETE") {
      return new Response("Ignored", { status: 200 });
    }

    const kirimMasuk =
      rec.masuk_menit !== null &&
      rec.wa_masuk_status === "pending" &&
      (payload.type === "INSERT" || payload.old_record?.masuk_menit === null);

    const kirimPulang =
      rec.pulang_menit !== null &&
      rec.wa_pulang_status === "pending" &&
      (payload.type === "INSERT" || payload.old_record?.pulang_menit === null);

    if (!kirimMasuk && !kirimPulang) {
      return new Response("Nothing to send", { status: 200 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: siswa, error: siswaErr } = await supabase
      .from("siswa")
      .select("nama, kelas, no_wa_ortu, sesi")
      .eq("nis", rec.nis)
      .single();

    if (siswaErr || !siswa) {
      return new Response("Siswa not found", { status: 404 });
    }

    const target = normalisasiWa(siswa.no_wa_ortu ?? "");
    const provider = (Deno.env.get("WA_PROVIDER") ?? "fonnte").toLowerCase();
    const infoKelas = siswa.kelas ? ` (${siswa.kelas})` : "";
    const updates: Record<string, string> = {};

    const kirim = async (pesan: string): Promise<string> => {
      if (!target) return "invalid_number";
      const ok = provider === "meta"
        ? await kirimViaMetaCloud(target, pesan)
        : await kirimViaFonnte(target, pesan);
      return ok ? "sent" : "failed";
    };

    if (kirimMasuk && rec.masuk_menit !== null) {
      const status = statusMasukLabel(siswa.sesi, rec.masuk_menit);
      const pesan = `Halo Bapak/Ibu, menginformasikan ananda ${siswa.nama}${infoKelas} telah absen MASUK pada pukul ${formatJam(rec.masuk_menit)} (${rec.tanggal}) dengan status ${status}.`;
      updates.wa_masuk_status = await kirim(pesan);
    }

    if (kirimPulang && rec.pulang_menit !== null) {
      const pesan = `Halo Bapak/Ibu, menginformasikan ananda ${siswa.nama}${infoKelas} telah absen PULANG pada pukul ${formatJam(rec.pulang_menit)} (${rec.tanggal}).`;
      updates.wa_pulang_status = await kirim(pesan);
    }

    if (Object.keys(updates).length > 0) {
      await supabase.from("absensi").update(updates).eq("id", rec.id);
    }

    return new Response(JSON.stringify({ ok: true, updates }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
