# Setup Backend Supabase + Auto-WA Ortu

1. **Buat Project Supabase** (Free Plan cukup) di `https://supabase.com`.
2. **Buat Tabel & Policy**: buka **SQL Editor**, tempel isi `supabase/schema.sql`, lalu **Run**.
3. **Buat Akun Guru**: buka **Authentication -> Users -> Add user** (buat 1 akun per guru atau 1 akun bersama untuk HP gerbang).
4. **Deploy Edge Function Auto-WA**:
   ```bash
   npx supabase login
   npx supabase link --project-ref <PROJECT_REF>
   npx supabase secrets set FONNTE_TOKEN=<TOKEN_FONNTE_ANDA>
   # Opsional jika pakai WA Cloud API Meta:
   # npx supabase secrets set WA_PROVIDER=meta META_WA_TOKEN=<TOKEN> META_WA_PHONE_ID=<PHONE_ID>
   npx supabase functions deploy send-wa-absensi --no-verify-jwt
   ```
5. **Pasang Database Webhook** di **Database -> Webhooks -> Create a new hook**:
   - Table: `public.absensi`
   - Events: `Insert`, `Update`
   - Type: `Supabase Edge Functions` -> `send-wa-absensi`
6. **Hubungkan di Aplikasi Android**:
   - Buka tab **Absen**, ketuk badge **Sync / Cloud** di kanan atas.
   - Isi **Supabase URL**, **Anon Key**, dan **Email + Password Guru** lalu tekan **Masuk & Sinkronkan**.
