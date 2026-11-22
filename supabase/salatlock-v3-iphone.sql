-- Salat Lock v3 : version iPhone (le Raccourci demande au serveur s'il faut bloquer).
-- À coller dans Supabase > SQL Editor puis « Run ». Peut être relancé sans danger.

-- Un « appareil » iPhone = un code secret aléatoire (aucune donnée personnelle).
create table if not exists public.ios_devices (
  token uuid primary key,
  user_id uuid references auth.users(id) on delete set null,
  windows jsonb not null default '[]'::jsonb,   -- créneaux de prière : [{p, day, lock_at, end_at}]
  excused_until timestamptz,
  updated_at timestamptz not null default now()
);
create table if not exists public.ios_done (
  token uuid not null references public.ios_devices(token) on delete cascade,
  day date not null,
  prayer text not null check (prayer in ('FAJR', 'DHUHR', 'ASR', 'MAGHRIB', 'ISHA')),
  created_at timestamptz not null default now(),
  primary key (token, day, prayer)
);
alter table public.ios_devices enable row level security;
alter table public.ios_done enable row level security;
revoke all on public.ios_devices, public.ios_done from anon, authenticated;

-- L'app iPhone envoie ses horaires (calculés sur le téléphone) pour les prochains jours.
create or replace function public.ios_register(p_token uuid, p_windows jsonb, p_excused_until timestamptz default null)
returns void language plpgsql security definer set search_path = public as $$
begin
  if jsonb_typeof(p_windows) <> 'array' or jsonb_array_length(p_windows) > 80 then
    raise exception 'horaires invalides';
  end if;
  insert into ios_devices (token, user_id, windows, excused_until)
  values (p_token, auth.uid(), p_windows, p_excused_until)
  on conflict (token) do update set
    windows = excluded.windows,
    excused_until = excluded.excused_until,
    user_id = coalesce(auth.uid(), ios_devices.user_id),
    updated_at = now();
end $$;

-- Le créneau en cours (s'il faut bloquer) ou null.
create or replace function public._ios_current(p_token uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select w from ios_devices d, jsonb_array_elements(d.windows) w
  where d.token = p_token
    and (d.excused_until is null or d.excused_until < now())
    and now() >= (w->>'lock_at')::timestamptz and now() < (w->>'end_at')::timestamptz
    and not exists (select 1 from ios_done x where x.token = p_token
                    and x.day = (w->>'day')::date and x.prayer = w->>'p')
  order by (w->>'lock_at')::timestamptz desc
  limit 1
$$;

-- Appelé par le Raccourci : true = il faut ouvrir Salat Lock.
create or replace function public.ios_gate(p_token uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select _ios_current(p_token) is not null
$$;

create or replace function public.ios_state(p_token uuid) returns json
language sql stable security definer set search_path = public as $$
  select json_build_object(
    'known', exists (select 1 from ios_devices where token = p_token),
    'current', _ios_current(p_token),
    'done', coalesce((select json_agg(json_build_object('day', day, 'p', prayer))
                      from ios_done where token = p_token and day >= current_date - 3), '[]'::json))
$$;

-- Prière faite (après le scan + le compte à rebours) : débloque et compte au classement.
create or replace function public.ios_done(p_token uuid, p_day date, p_prayer text) returns void
language plpgsql security definer set search_path = public as $$
declare uid uuid;
begin
  if p_day < current_date - 2 or p_day > current_date + 1 then raise exception 'date hors limites'; end if;
  select user_id into uid from ios_devices where token = p_token;
  if not found then raise exception 'appareil inconnu'; end if;
  insert into ios_done (token, day, prayer) values (p_token, p_day, upper(p_prayer)) on conflict do nothing;
  if uid is not null and exists (select 1 from profiles where id = uid) then
    insert into prayer_log (user_id, day, prayer) values (uid, p_day, upper(p_prayer)) on conflict do nothing;
    perform _recompute(uid);
  end if;
end $$;

revoke all on function public._ios_current(uuid) from public, anon, authenticated;
revoke all on function public.ios_register(uuid, jsonb, timestamptz), public.ios_gate(uuid),
  public.ios_state(uuid), public.ios_done(uuid, date, text) from public;
grant execute on function public.ios_register(uuid, jsonb, timestamptz), public.ios_gate(uuid),
  public.ios_state(uuid), public.ios_done(uuid, date, text) to anon, authenticated;

-- Nettoyage des joueurs de test créés pendant les essais
delete from auth.users where id in (select id from public.profiles where pseudo in ('TestClaudeA', 'TestClaudeB'));
