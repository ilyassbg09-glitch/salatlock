-- Salat Lock v2 : classement + compteur d'installations. À coller dans Supabase > SQL Editor puis « Run ».
--  Salat Lock : classement en ligne
--  À coller dans Supabase > SQL Editor, puis appuyer sur « Run ».
--  Peut être relancé sans danger (il ne supprime aucune donnée).
create extension if not exists pgcrypto;

-- Profils publics : pseudo + statistiques
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  pseudo text not null,
  friend_code text not null unique default upper(substr(encode(gen_random_bytes(6), 'hex'), 1, 6)),
  total int not null default 0,
  streak int not null default 0,
  best_streak int not null default 0,
  last_active_day date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint pseudo_ok check (char_length(pseudo) between 3 and 20 and pseudo ~ '^[[:alnum:]_. -]+$')
);
create unique index if not exists profiles_pseudo_unique on public.profiles (lower(pseudo));

-- Journal des prières faites (une ligne par prière et par jour)
create table if not exists public.prayer_log (
  user_id uuid not null references public.profiles(id) on delete cascade,
  day date not null,
  prayer text not null check (prayer in ('FAJR', 'DHUHR', 'ASR', 'MAGHRIB', 'ISHA')),
  created_at timestamptz not null default now(),
  primary key (user_id, day, prayer)
);

-- Jours de dispense (règles, maladie, voyage) : ne cassent pas la série
create table if not exists public.excused_days (
  user_id uuid not null references public.profiles(id) on delete cascade,
  day date not null,
  primary key (user_id, day)
);

-- Amis
create table if not exists public.friends (
  user_id uuid not null references public.profiles(id) on delete cascade,
  friend_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, friend_id),
  check (user_id <> friend_id)
);

-- Sécurité : personne ne modifie les tables directement, tout passe par les fonctions.
alter table public.profiles enable row level security;
alter table public.prayer_log enable row level security;
alter table public.excused_days enable row level security;
alter table public.friends enable row level security;
revoke all on public.profiles, public.prayer_log, public.excused_days, public.friends from anon, authenticated;

-- ---------------------------------------------------------------------
-- Recalcul des statistiques (côté serveur, pour limiter la triche)
-- ---------------------------------------------------------------------
create or replace function public._recompute(uid uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  last_full date; last_exc date; last_act date; d date; s int := 0; n int := 0;
begin
  select max(day) into last_full from (
    select day from prayer_log where user_id = uid group by day having count(*) = 5) f;
  select max(day) into last_exc from excused_days where user_id = uid;
  last_act := greatest(last_full, last_exc);
  if last_full is not null then
    d := last_act;
    while n < 4000 loop
      if exists (select 1 from prayer_log where user_id = uid and day = d group by day having count(*) = 5) then
        s := s + 1;
      elsif not exists (select 1 from excused_days where user_id = uid and day = d) then
        exit;
      end if;
      d := d - 1; n := n + 1;
    end loop;
  end if;
  update profiles set
    total = (select count(*) from prayer_log where user_id = uid),
    streak = s,
    best_streak = greatest(best_streak, s),
    last_active_day = last_act,
    updated_at = now()
  where id = uid;
end $$;

-- Série « en cours » : remise à zéro si plus d'activité depuis 2 jours
create or replace function public._live_streak(p public.profiles) returns int
language sql stable as $$
  select case when p.last_active_day >= current_date - 2 then p.streak else 0 end
$$;

create or replace function public.my_profile() returns json
language plpgsql security definer set search_path = public as $$
declare p profiles; r int;
begin
  select * into p from profiles where id = auth.uid();
  if not found then return null; end if;
  select 1 + count(*) into r from profiles q
   where (_live_streak(q), q.total) > (_live_streak(p), p.total);
  return json_build_object('pseudo', p.pseudo, 'friend_code', p.friend_code, 'total', p.total,
    'streak', _live_streak(p), 'best_streak', p.best_streak, 'rank', r,
    'players', (select count(*) from profiles));
end $$;

create or replace function public.set_pseudo(p_pseudo text) returns json
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'non connecté'; end if;
  insert into profiles (id, pseudo) values (auth.uid(), trim(p_pseudo))
  on conflict (id) do update set pseudo = excluded.pseudo, updated_at = now();
  return my_profile();
exception when unique_violation then
  raise exception 'pseudo déjà pris' using errcode = 'P0001';
end $$;

create or replace function public.record_prayer(p_day date, p_prayer text) returns json
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or not exists (select 1 from profiles where id = auth.uid()) then
    raise exception 'profil introuvable';
  end if;
  if p_day < current_date - 2 or p_day > current_date + 1 then
    raise exception 'date hors limites';
  end if;
  insert into prayer_log (user_id, day, prayer) values (auth.uid(), p_day, upper(p_prayer))
  on conflict do nothing;
  perform _recompute(auth.uid());
  return my_profile();
end $$;

create or replace function public.record_excused(p_day date) returns json
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or not exists (select 1 from profiles where id = auth.uid()) then
    raise exception 'profil introuvable';
  end if;
  if p_day < current_date - 2 or p_day > current_date + 1 then
    raise exception 'date hors limites';
  end if;
  -- au maximum 12 jours de dispense sur 30 jours glissants
  if (select count(*) from excused_days where user_id = auth.uid() and day > current_date - 30) >= 12 then
    return my_profile();
  end if;
  insert into excused_days (user_id, day) values (auth.uid(), p_day) on conflict do nothing;
  perform _recompute(auth.uid());
  return my_profile();
end $$;

create or replace function public.global_leaderboard(p_limit int default 100)
returns table (rank bigint, pseudo text, streak int, total int, best_streak int, is_me boolean)
language sql security definer set search_path = public stable as $$
  select row_number() over (order by _live_streak(p) desc, p.total desc, p.created_at),
         p.pseudo, _live_streak(p), p.total, p.best_streak, p.id = auth.uid()
  from profiles p
  order by 1
  limit least(greatest(p_limit, 1), 200)
$$;

create or replace function public.friends_leaderboard()
returns table (rank bigint, pseudo text, streak int, total int, best_streak int, is_me boolean)
language sql security definer set search_path = public stable as $$
  select row_number() over (order by _live_streak(p) desc, p.total desc, p.created_at),
         p.pseudo, _live_streak(p), p.total, p.best_streak, p.id = auth.uid()
  from profiles p
  where p.id = auth.uid()
     or p.id in (select friend_id from friends where user_id = auth.uid())
  order by 1
$$;

create or replace function public.add_friend(p_code text) returns text
language plpgsql security definer set search_path = public as $$
declare fid uuid; fp text;
begin
  select id, pseudo into fid, fp from profiles where friend_code = upper(trim(p_code));
  if fid is null then raise exception 'code inconnu' using errcode = 'P0001'; end if;
  if fid = auth.uid() then raise exception 'c''est ton propre code' using errcode = 'P0001'; end if;
  insert into friends (user_id, friend_id) values (auth.uid(), fid) on conflict do nothing;
  return fp;
end $$;

create or replace function public.remove_friend(p_pseudo text) returns void
language sql security definer set search_path = public as $$
  delete from friends where user_id = auth.uid()
    and friend_id = (select id from profiles where lower(pseudo) = lower(p_pseudo));
$$;

-- Supprimer définitivement son profil et toutes ses données
create or replace function public.delete_my_account() returns void
language sql security definer set search_path = public as $$
  delete from auth.users where id = auth.uid();
$$;

-- Droits d'exécution
revoke all on function public._recompute(uuid) from public, anon, authenticated;
revoke all on function public.my_profile(), public.set_pseudo(text), public.record_prayer(date, text),
  public.record_excused(date), public.friends_leaderboard(), public.add_friend(text),
  public.remove_friend(text), public.delete_my_account() from public, anon;
grant execute on function public.my_profile(), public.set_pseudo(text), public.record_prayer(date, text),
  public.record_excused(date), public.friends_leaderboard(), public.add_friend(text),
  public.remove_friend(text), public.delete_my_account() to authenticated;
grant execute on function public.global_leaderboard(int) to anon, authenticated;

-- =====================================================================
--  Compteur anonyme d'installations + statistiques
-- =====================================================================
create table if not exists public.installs (
  install_id uuid primary key,
  platform text not null check (platform in ('android', 'ios')),
  app_version text,
  first_seen date not null default current_date,
  last_seen date not null default current_date
);
create table if not exists public.daily_active (
  install_id uuid not null references public.installs(install_id) on delete cascade,
  day date not null,
  primary key (install_id, day)
);
create table if not exists public.app_secrets (name text primary key, value text not null);
alter table public.installs enable row level security;
alter table public.daily_active enable row level security;
alter table public.app_secrets enable row level security;
revoke all on public.installs, public.daily_active, public.app_secrets from anon, authenticated;
insert into public.app_secrets (name, value) values ('stats_hash', '4b3a6e8856175f3c5e88d6810b9097ab31fbe581dba2e82cad9d6d57f3ad6e03')
  on conflict (name) do update set value = excluded.value;

create or replace function public.ping(p_install uuid, p_platform text, p_version text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_platform not in ('android', 'ios') then return; end if;
  insert into installs (install_id, platform, app_version) values (p_install, p_platform, left(p_version, 20))
  on conflict (install_id) do update set last_seen = current_date, app_version = left(excluded.app_version, 20);
  insert into daily_active (install_id, day) values (p_install, current_date) on conflict do nothing;
end $$;

create or replace function public.stats(p_code text) returns json
language plpgsql security definer set search_path = public, extensions as $$
begin
  if encode(digest(coalesce(p_code, ''), 'sha256'), 'hex') <> (select value from app_secrets where name = 'stats_hash') then
    raise exception 'code incorrect' using errcode = 'P0001';
  end if;
  return json_build_object(
    'installs', (select count(*) from installs),
    'android', (select count(*) from installs where platform = 'android'),
    'ios', (select count(*) from installs where platform = 'ios'),
    'active_today', (select count(*) from daily_active where day = current_date),
    'active_7d', (select count(distinct install_id) from daily_active where day > current_date - 7),
    'active_30d', (select count(distinct install_id) from daily_active where day > current_date - 30),
    'players', (select count(*) from profiles),
    'prayers_today', (select count(*) from prayer_log where day = current_date),
    'prayers_total', (select count(*) from prayer_log),
    'new_per_day', (select coalesce(json_agg(json_build_object('day', d, 'n', n) order by d), '[]'::json) from (
        select first_seen as d, count(*) as n from installs where first_seen > current_date - 30 group by first_seen) x),
    'versions', (select coalesce(json_object_agg(v, n), '{}'::json) from (
        select coalesce(app_version, '?') as v, count(*) as n from installs where last_seen > current_date - 30 group by 1) y)
  );
end $$;

revoke all on function public.ping(uuid, text, text), public.stats(text) from public;
grant execute on function public.ping(uuid, text, text), public.stats(text) to anon, authenticated;
