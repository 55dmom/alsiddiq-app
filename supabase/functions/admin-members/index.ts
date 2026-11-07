// admin-members — the only way accounts are created or changed.
// Runs on Supabase with the server key Supabase injects into its own functions; the key never leaves the server.
const BASE = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type" };
const out = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

async function call(method: string, path: string, body?: unknown, extra: Record<string, string> = {}) {
  const r = await fetch(BASE + path, {
    method,
    headers: { apikey: KEY, Authorization: `Bearer ${KEY}`, "Content-Type": "application/json", ...extra },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { ok: r.ok, status: r.status, data };
}
const rest = (m: string, p: string, b?: unknown) => call(m, "/rest/v1/" + p, b, { Prefer: "return=representation" });
const errMsg = (d: any) => (d && (d.msg || d.message || d.error_description || d.error)) || "خطأ في الخادم";

const digits = (p: string) =>
  String(p ?? "").replace(/[٠-٩]/g, (d) => String("٠١٢٣٤٥٦٧٨٩".indexOf(d))).replace(/\D/g, "");
const emailFor = (phone: string) => `${digits(phone)}@alsiddiq.app`;
const ROLES = ["admin", "teacher", "student", "parent"];

async function hasAccounts() {
  const r = await rest("GET", "profiles?select=id&limit=1");
  return Array.isArray(r.data) && r.data.length > 0;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return out(200, {});
  let body: any;
  try { body = await req.json(); } catch { return out(400, { error: "طلب غير صالح" }); }
  const action = body?.action;

  if (action === "status") return out(200, { needs_setup: !(await hasAccounts()) });

  // One-time setup: only while the database has no accounts at all.
  if (action === "bootstrap") {
    if (await hasAccounts()) return out(403, { error: "تم إعداد المركز مسبقاً" });
    const phone = digits(body.phone);
    if (phone.length < 8) return out(400, { error: "رقم الجوال غير صحيح" });
    if (String(body.password ?? "").length < 6) return out(400, { error: "كلمة المرور 6 أحرف على الأقل" });
    const u = await call("POST", "/auth/v1/admin/users", { email: emailFor(phone), password: body.password, email_confirm: true });
    if (!u.ok) return out(400, { error: errMsg(u.data) });
    let center = (await rest("GET", "centers?select=*&limit=1")).data?.[0];
    if (!center) {
      const c = await rest("POST", "centers", { name: body.center_name || "مركز الصديق لتعليم القرآن الكريم", city: body.city || "" });
      if (!c.ok) return out(500, { error: errMsg(c.data) });
      center = c.data[0];
    }
    const p = await rest("POST", "profiles", { id: u.data.id, center_id: center.id, full_name: body.full_name || "إدارة المركز", phone, role: "admin" });
    if (!p.ok) { await call("DELETE", `/auth/v1/admin/users/${u.data.id}`); return out(500, { error: errMsg(p.data) }); }
    return out(200, { ok: true });
  }

  // Everything else: the caller must be an active admin.
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const who = await fetch(BASE + "/auth/v1/user", { headers: { apikey: KEY, Authorization: `Bearer ${token}` } });
  if (!who.ok) return out(401, { error: "انتهت الجلسة، سجّل الدخول مجدداً" });
  const caller = await who.json();
  const meR = await rest("GET", `profiles?select=id,role,center_id,active&id=eq.${caller.id}`);
  const me = meR.data?.[0];
  if (!me || !me.active || me.role !== "admin") return out(403, { error: "ليست لديك صلاحية لهذه العملية" });

  if (action === "create") {
    const phone = digits(body.phone);
    if (!String(body.full_name ?? "").trim()) return out(400, { error: "اكتب الاسم" });
    if (phone.length < 8) return out(400, { error: "رقم الجوال غير صحيح" });
    if (!ROLES.includes(body.role)) return out(400, { error: "نوع الحساب غير صحيح" });
    if (String(body.password ?? "").length < 6) return out(400, { error: "كلمة المرور 6 أحرف على الأقل" });
    const dup = await rest("GET", `profiles?select=id&center_id=eq.${me.center_id}&phone=eq.${phone}`);
    if (dup.data?.length) return out(409, { error: "هذا الرقم مسجّل مسبقاً" });
    const u = await call("POST", "/auth/v1/admin/users", { email: emailFor(phone), password: body.password, email_confirm: true });
    if (!u.ok) return out(400, { error: String(errMsg(u.data)).includes("already") ? "هذا الرقم مسجّل مسبقاً" : errMsg(u.data) });
    const row = { id: u.data.id, center_id: me.center_id, full_name: String(body.full_name).trim(), phone, role: body.role,
      student_id: body.role === "student" ? (body.student_id ?? null) : null };
    const p = await rest("POST", "profiles", row);
    if (!p.ok) { await call("DELETE", `/auth/v1/admin/users/${u.data.id}`); return out(500, { error: errMsg(p.data) }); }
    const kids: string[] = body.role === "parent" && Array.isArray(body.child_ids) ? body.child_ids : [];
    if (kids.length) await rest("POST", "parent_students", kids.map((s) => ({ parent_id: u.data.id, student_id: s })));
    return out(200, { profile: p.data[0], child_ids: kids });
  }

  // The target account must belong to the same centre.
  const tR = await rest("GET", `profiles?select=*&id=eq.${encodeURIComponent(body.id ?? "")}`);
  const target = tR.data?.[0];
  if (!target || target.center_id !== me.center_id) return out(404, { error: "الحساب غير موجود" });

  if (action === "update") {
    const patch: Record<string, unknown> = {};
    if (typeof body.full_name === "string" && body.full_name.trim()) patch.full_name = body.full_name.trim();
    if (typeof body.active === "boolean") {
      if (target.id === me.id && body.active === false) return out(400, { error: "لا يمكنك إيقاف حسابك" });
      patch.active = body.active;
    }
    if (target.role === "student" && "student_id" in body) patch.student_id = body.student_id ?? null;
    if (typeof body.phone === "string" && digits(body.phone) !== target.phone) {
      const phone = digits(body.phone);
      if (phone.length < 8) return out(400, { error: "رقم الجوال غير صحيح" });
      const u = await call("PUT", `/auth/v1/admin/users/${target.id}`, { email: emailFor(phone), email_confirm: true });
      if (!u.ok) return out(400, { error: errMsg(u.data) });
      patch.phone = phone;
    }
    if (Object.keys(patch).length) {
      const r = await rest("PATCH", `profiles?id=eq.${target.id}`, patch);
      if (!r.ok) return out(500, { error: errMsg(r.data) });
    }
    if (target.role === "parent" && Array.isArray(body.child_ids)) {
      await rest("DELETE", `parent_students?parent_id=eq.${target.id}`);
      if (body.child_ids.length) await rest("POST", "parent_students", body.child_ids.map((s: string) => ({ parent_id: target.id, student_id: s })));
    }
    return out(200, { ok: true });
  }

  if (action === "reset_password") {
    if (String(body.password ?? "").length < 6) return out(400, { error: "كلمة المرور 6 أحرف على الأقل" });
    const u = await call("PUT", `/auth/v1/admin/users/${target.id}`, { password: body.password });
    if (!u.ok) return out(400, { error: errMsg(u.data) });
    return out(200, { ok: true });
  }

  return out(400, { error: "عملية غير معروفة" });
});
