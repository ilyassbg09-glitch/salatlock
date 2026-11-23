-- Salat Lock v4 : notifications iPhone à l'heure de l'adhan.
-- Installé automatiquement par supabase/deploy-notifs.sh (avec le jeton). Peut être relancé sans danger.

create table if not exists public.push_subs (
  endpoint text primary key,
  token uuid not null references public.ios_devices(token) on delete cascade,
  p256dh text not null,
  auth text not null,
  created_at timestamptz not null default now()
);
create table if not exists public.push_sent (
  token uuid not null,
  day date not null,
  prayer text not null,
  sent_at timestamptz not null default now(),
  primary key (token, day, prayer)
);
alter table public.push_subs enable row level security;
alter table public.push_sent enable row level security;
revoke all on public.push_subs, public.push_sent from anon, authenticated;

-- L'iPhone s'abonne (après avoir dit « Autoriser »).
create or replace function public.push_subscribe(p_token uuid, p_sub jsonb) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from ios_devices where token = p_token) then raise exception 'appareil inconnu'; end if;
  if coalesce(p_sub->>'endpoint', '') not like 'https://%' then raise exception 'abonnement invalide'; end if;
  insert into push_subs (endpoint, token, p256dh, auth)
  values (p_sub->>'endpoint', p_token, p_sub->'keys'->>'p256dh', p_sub->'keys'->>'auth')
  on conflict (endpoint) do update set token = excluded.token, p256dh = excluded.p256dh, auth = excluded.auth;
end $$;

create or replace function public.push_unsubscribe(p_endpoint text) returns void
language sql security definer set search_path = public as $$ delete from push_subs where endpoint = p_endpoint $$;

-- Utilisé par la fonction d'envoi (clé de service uniquement) : les notifications à envoyer maintenant.
create or replace function public.push_due() returns table (endpoint text, p256dh text, auth text, prayer text, day date, grace int)
language plpgsql security definer set search_path = public as $$
begin
  return query
  with due as (
    select d.token, w->>'p' as p, (w->>'day')::date as dd,
           greatest(0, round(extract(epoch from ((w->>'lock_at')::timestamptz - (w->>'adhan_at')::timestamptz)) / 60))::int as g
    from ios_devices d, jsonb_array_elements(d.windows) w
    where w ? 'adhan_at' and coalesce((w->>'test')::boolean, false) = false
      and (d.excused_until is null or d.excused_until < now())
      and now() >= (w->>'adhan_at')::timestamptz and now() < (w->>'adhan_at')::timestamptz + interval '10 minutes'
      and not exists (select 1 from ios_done x where x.token = d.token and x.day = (w->>'day')::date and x.prayer = w->>'p')
      and not exists (select 1 from push_sent s where s.token = d.token and s.day = (w->>'day')::date and s.prayer = w->>'p')
  ), mark as (
    insert into push_sent (token, day, prayer) select token, dd, p from due on conflict do nothing returning token
  )
  select s.endpoint, s.p256dh, s.auth, due.p, due.dd, due.g from due join push_subs s on s.token = due.token;
  delete from push_sent where sent_at < now() - interval '4 days';
end $$;

revoke all on function public.push_due() from public, anon, authenticated;
revoke all on function public.push_subscribe(uuid, jsonb), public.push_unsubscribe(text) from public;
grant execute on function public.push_subscribe(uuid, jsonb), public.push_unsubscribe(text) to anon, authenticated;
