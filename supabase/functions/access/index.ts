// access — the only door into «مركز الصديق» v3.
// One password field: the server works out whose password it is (viewer / admin / which teacher),
// by bcrypt-comparing it with the hashes Supabase Auth stores, then returns that account's session.
// Passwords are never stored or compared in the app. The service key exists only here, on the server.
const BASE = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON = Deno.env.get("SUPABASE_ANON_KEY")!;
const DOMAIN = "access.alsiddiq.app";
const MIN_LEN = 6;
const IP_FAILS = 6;        // wrong attempts per address …
const IP_WINDOW_MIN = 15;  // … per 15 minutes
const ALL_FAILS = 60;      // wrong attempts from everyone per 10 minutes
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type" };
const out = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function call(method: string, path: string, body?: unknown, extra: Record<string, string> = {}, key = KEY) {
  const r = await fetch(BASE + path, {
    method,
    headers: { apikey: key, Authorization: `Bearer ${key}`, "Content-Type": "application/json", ...extra },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { ok: r.ok, status: r.status, data };
}
const rest = (m: string, p: string, b?: unknown) => call(m, "/rest/v1/" + p, b, { Prefer: "return=representation" });
const rpc = (fn: string, args: unknown) => call("POST", "/rest/v1/rpc/" + fn, args);
const errMsg = (d: any) => (d && (d.msg || d.message || d.error_description || d.error)) || "خطأ في الخادم";
const clean = (s: unknown) => String(s ?? "").trim();
const normPw = (s: unknown) => String(s ?? "").replace(/[٠-٩]/g, (d) => String("٠١٢٣٤٥٦٧٨٩".indexOf(d))).trim();

async function theCenter(): Promise<any | null> {
  const r = await rest("GET", "centers?select=*&order=created_at.asc&limit=1");
  return Array.isArray(r.data) && r.data.length ? r.data[0] : null;
}
async function accounts(center: string, kind?: string) {
  const r = await rest("GET", `profiles?select=id,full_name,role,access_kind,active&center_id=eq.${center}&access_kind=not.is.null` + (kind ? `&access_kind=eq.${kind}` : ""));
  return Array.isArray(r.data) ? r.data : [];
}
async function matches(center: string, kind: string | null, pw: string): Promise<any[]> {
  const r = await rpc("access_match", { p_center: center, p_kind: kind, p_password: pw });
  return Array.isArray(r.data) ? r.data : [];
}
async function passwordTaken(center: string, pw: string, exceptId?: string) {
  return (await matches(center, null, pw)).some((m) => m.user_id !== exceptId);
}
function checkPw(pw: string) {
  if (pw.length < MIN_LEN) return `كلمة السر ${MIN_LEN} أحرف أو أرقام على الأقل`;
  return null;
}
async function newAccount(center: string, kind: string, role: string, name: string, pw: string) {
  const email = `${kind}-${crypto.randomUUID()}@${DOMAIN}`;
  const u = await call("POST", "/auth/v1/admin/users", { email, password: pw, email_confirm: true, app_metadata: { access_kind: kind } });
  if (!u.ok) throw new Error(errMsg(u.data));
  const id = u.data.id ?? u.data.user?.id;
  const p = await rest("POST", "profiles", { id, center_id: center, full_name: name, phone: "", role, access_kind: kind, active: true });
  if (!p.ok) { await call("DELETE", `/auth/v1/admin/users/${id}`); throw new Error(errMsg(p.data)); }
  return p.data[0];
}
async function setPassword(id: string, pw: string) {
  const r = await call("PUT", `/auth/v1/admin/users/${id}`, { password: pw });
  if (!r.ok) throw new Error(errMsg(r.data));
  await rpc("access_revoke", { p_user: id });
}
async function sessionFor(email: string, pw: string) {
  const r = await call("POST", "/auth/v1/token?grant_type=password", { email, password: pw }, {}, ANON);
  if (!r.ok) throw new Error(errMsg(r.data));
  return r.data;
}
async function circlesOf(center: string, teacher: string) {
  const r = await rest("GET", `circles?select=id&center_id=eq.${center}&teacher_id=eq.${teacher}`);
  return Array.isArray(r.data) ? r.data.map((c: any) => c.id) : [];
}
async function profileOut(center: string, p: any) {
  return { id: p.id, full_name: p.full_name, role: p.role, access_kind: p.access_kind,
           circle_ids: p.access_kind === "teacher" ? await circlesOf(center, p.id) : [] };
}
/** The caller's v3 profile from their access token, or null. */
async function caller(req: Request) {
  const tok = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!tok || tok === ANON) return null;
  const u = await call("GET", "/auth/v1/user", undefined, { Authorization: `Bearer ${tok}` });
  if (!u.ok || !u.data?.id) return null;
  const p = await rest("GET", `profiles?select=*&id=eq.${u.data.id}&active=is.true`);
  return Array.isArray(p.data) && p.data.length ? p.data[0] : null;
}
function ipOf(req: Request) {
  return (req.headers.get("x-forwarded-for") ?? req.headers.get("cf-connecting-ip") ?? "").split(",")[0].trim() || "unknown";
}
async function throttled(ip: string) {
  const since = new Date(Date.now() - IP_WINDOW_MIN * 60_000).toISOString();
  const mine = await rest("GET", `access_attempts?select=id&ok=is.false&ip=eq.${encodeURIComponent(ip)}&at=gte.${since}`);
  if (Array.isArray(mine.data) && mine.data.length >= IP_FAILS) return true;
  const since10 = new Date(Date.now() - 10 * 60_000).toISOString();
  const all = await rest("GET", `access_attempts?select=id&ok=is.false&at=gte.${since10}&limit=${ALL_FAILS}`);
  return Array.isArray(all.data) && all.data.length >= ALL_FAILS;
}
async function logAttempt(ip: string, kind: string | null, ok: boolean) {
  await rest("POST", "access_attempts", { ip, kind, ok });
  if (Math.random() < 0.05) await rest("DELETE", `access_attempts?at=lt.${new Date(Date.now() - 86_400_000).toISOString()}`);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return out(200, {});
  let body: any;
  try { body = await req.json(); } catch { return out(400, { error: "طلب غير صالح" }); }
  const action = body?.action;
  try {
    const center = await theCenter();

    if (action === "status") {
      const admins = center ? await accounts(center.id, "admin") : [];
      return out(200, { needs_setup: admins.length === 0, center_name: center?.name ?? "" });
    }

    // First run: centre name + admin password + general (viewer) password. Only while no admin password exists.
    if (action === "setup") {
      if (center && (await accounts(center.id, "admin")).length > 0) return out(403, { error: "تم إعداد المركز مسبقاً" });
      const name = clean(body.center_name) || "مركز الصديق لتعليم القرآن الكريم";
      const apw = normPw(body.admin_password), vpw = normPw(body.viewer_password);
      const bad = checkPw(apw) ?? checkPw(vpw);
      if (bad) return out(400, { error: bad });
      if (apw === vpw) return out(400, { error: "كلمة سر الإدارة يجب أن تختلف عن كلمة السر العامة" });
      let c = center;
      if (c) { await rest("PATCH", `centers?id=eq.${c.id}`, { name }); }
      else { const r = await rest("POST", "centers", { name }); if (!r.ok) throw new Error(errMsg(r.data)); c = r.data[0]; }
      for (const old of await accounts(c.id, "viewer")) await rest("PATCH", `profiles?id=eq.${old.id}`, { active: false });
      const admin = await newAccount(c.id, "admin", "admin", "الإدارة", apw);
      await newAccount(c.id, "viewer", "viewer", "عرض عام", vpw);
      const s = await sessionFor((await call("GET", `/auth/v1/admin/users/${admin.id}`)).data.email, apw);
      return out(200, { session: s, profile: await profileOut(c.id, admin), center_name: name });
    }

    // Enter a password. kind: null (main screen: any), "admin" (Settings → الإدارة), "teacher" (Settings → المعلمين).
    if (action === "login") {
      if (!center) return out(409, { error: "المركز لم يُجهّز بعد" });
      const kind = ["admin", "teacher", "viewer"].includes(body.kind) ? body.kind : null;
      const pw = normPw(body.password);
      const ip = ipOf(req);
      if (await throttled(ip)) return out(429, { error: "محاولات خاطئة كثيرة، انتظر ربع ساعة ثم حاول" });
      const found = pw.length >= 1 ? await matches(center.id, kind, pw) : [];
      if (found.length === 0) {
        await logAttempt(ip, kind, false);
        await sleep(1200);
        return out(401, { error: "كلمة السر غير صحيحة" });
      }
      const order = ["admin", "teacher", "viewer"];
      found.sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind));
      const m = found[0];
      await logAttempt(ip, kind, true);
      const s = await sessionFor(m.email, pw);
      const p = (await rest("GET", `profiles?select=*&id=eq.${m.user_id}`)).data[0];
      return out(200, { session: s, profile: await profileOut(center.id, p), center_name: center.name });
    }

    // ---------- everything below: the admin only ----------
    const me = await caller(req);
    if (!me || me.access_kind !== "admin" || !center || me.center_id !== center.id) return out(403, { error: "هذه العملية للإدارة فقط" });

    if (action === "set_password") {             // target: "viewer" | "admin"
      const target = body.target === "admin" ? "admin" : body.target === "viewer" ? "viewer" : null;
      if (!target) return out(400, { error: "طلب غير صالح" });
      const pw = normPw(body.password); const bad = checkPw(pw); if (bad) return out(400, { error: bad });
      const acc = target === "admin" ? me : (await accounts(center.id, "viewer")).find((a: any) => a.active);
      if (await passwordTaken(center.id, pw, acc?.id)) return out(409, { error: "كلمة السر هذي مستخدمة لحساب ثاني، اختر غيرها" });
      if (acc) await setPassword(acc.id, pw);
      else await newAccount(center.id, "viewer", "viewer", "عرض عام", pw);
      return out(200, { ok: true });
    }

    if (action === "teacher_save") {             // id? name, circle_id?, password? (required when new)
      const name = clean(body.name);
      if (!name) return out(400, { error: "اكتب اسم المعلم" });
      const pw = body.password ? normPw(body.password) : "";
      let id = clean(body.id);
      if (!id && !pw) return out(400, { error: "اختر كلمة سر للمعلم" });
      if (pw) { const bad = checkPw(pw); if (bad) return out(400, { error: bad });
                if (await passwordTaken(center.id, pw, id || undefined)) return out(409, { error: "كلمة السر هذي مستخدمة لحساب ثاني، اختر غيرها" }); }
      if (id) {
        const t = (await accounts(center.id, "teacher")).find((a: any) => a.id === id);
        if (!t) return out(404, { error: "المعلم غير موجود" });
        await rest("PATCH", `profiles?id=eq.${id}`, { full_name: name, active: true });
        if (pw) await setPassword(id, pw);
      } else {
        id = (await newAccount(center.id, "teacher", "teacher", name, pw)).id;
      }
      if (body.circle_id !== undefined) {
        const cid = clean(body.circle_id);
        // One circle per teacher in this screen: release their other circles, then take this one.
        await rest("PATCH", `circles?center_id=eq.${center.id}&teacher_id=eq.${id}` + (cid ? `&id=neq.${cid}` : ""), { teacher_id: null });
        if (cid) await rest("PATCH", `circles?id=eq.${cid}&center_id=eq.${center.id}`, { teacher_id: id });
      }
      const p = (await rest("GET", `profiles?select=*&id=eq.${id}`)).data[0];
      return out(200, { profile: await profileOut(center.id, p) });
    }

    if (action === "teacher_remove") {           // kept as a record (their past entries keep their name), but can't sign in
      const id = clean(body.id);
      const t = (await accounts(center.id, "teacher")).find((a: any) => a.id === id);
      if (!t) return out(404, { error: "المعلم غير موجود" });
      await rest("PATCH", `profiles?id=eq.${id}`, { active: false });
      await rest("PATCH", `circles?teacher_id=eq.${id}`, { teacher_id: null });
      await call("PUT", `/auth/v1/admin/users/${id}`, { password: crypto.randomUUID() + crypto.randomUUID() });
      await rpc("access_revoke", { p_user: id });
      return out(200, { ok: true });
    }

    return out(400, { error: "طلب غير معروف" });
  } catch (e) {
    return out(500, { error: String((e as Error)?.message ?? e) });
  }
});
