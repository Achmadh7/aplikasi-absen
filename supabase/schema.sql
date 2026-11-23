-- Jalankan di Supabase SQL Editor (Dashboard -> SQL Editor -> New query)

create table if not exists public.siswa (
  nis text primary key,
  nama text not null,
  kelas text not null default '',
  no_wa_ortu text not null default '',
  sesi text not null default 'PAGI' check (sesi in ('PAGI', 'SIANG')),
  aktif boolean not null default true,
  updated_at timestamptz not null default now()
);

create table if not exists public.absensi (
  id bigint generated always as identity primary key,
  nis text not null references public.siswa(nis) on delete cascade,
  tanggal date not null,
  masuk_menit int,
  pulang_menit int,
  device_id text not null default '',
  wa_masuk_status text not null default 'pending',
  wa_pulang_status text not null default 'pending',
  updated_at timestamptz not null default now(),
  unique (nis, tanggal)
);

create table if not exists public.izin (
  id bigint generated always as identity primary key,
  nis text not null references public.siswa(nis) on delete cascade,
  tanggal date not null,
  jenis text not null check (jenis in ('IZIN', 'SAKIT')),
  keterangan text not null default '',
  updated_at timestamptz not null default now(),
  unique (nis, tanggal)
);

-- RPC idempotent agar banyak HP tidak menimpa masuk/pulang satu sama lain
create or replace function public.upsert_absensi(
  p_nis text,
  p_tanggal date,
  p_masuk_menit int,
  p_pulang_menit int,
  p_device_id text default ''
)
returns public.absensi
language plpgsql
security definer
as $$
declare
  v_row public.absensi;
begin
  insert into public.absensi (nis, tanggal, masuk_menit, pulang_menit, device_id)
  values (p_nis, p_tanggal, p_masuk_menit, p_pulang_menit, coalesce(p_device_id, ''))
  on conflict (nis, tanggal) do update set
    masuk_menit = coalesce(public.absensi.masuk_menit, excluded.masuk_menit),
    pulang_menit = coalesce(public.absensi.pulang_menit, excluded.pulang_menit),
    device_id = case
      when excluded.device_id <> '' then excluded.device_id
      else public.absensi.device_id
    end,
    updated_at = now()
  returning * into v_row;

  return v_row;
end;
$$;

-- RLS: hanya guru/perangkat yang login via Supabase Auth yang bisa baca/tulis
alter table public.siswa enable row level security;
alter table public.absensi enable row level security;
alter table public.izin enable row level security;

drop policy if exists "auth_all_siswa" on public.siswa;
create policy "auth_all_siswa" on public.siswa
  for all to authenticated using (true) with check (true);

drop policy if exists "auth_all_absensi" on public.absensi;
create policy "auth_all_absensi" on public.absensi
  for all to authenticated using (true) with check (true);

drop policy if exists "auth_all_izin" on public.izin;
create policy "auth_all_izin" on public.izin
  for all to authenticated using (true) with check (true);

grant execute on function public.upsert_absensi(text, date, int, int, text) to authenticated;
