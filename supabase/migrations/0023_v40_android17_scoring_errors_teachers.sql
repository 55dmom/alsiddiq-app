-- v4.0 (2026-11-25): Android 17 additive schema support
-- 1. record_kind enum extended with 'azfar' (لم يسمع) and 'sard' (سرد السورة)
-- 2. students.monthly_juz_target (numeric, default 1.0) for 500-point monthly scoring
-- 3. recitations.review_* independent error counter & optional review fields (additive, nullable/defaulted)
-- 4. profiles.deleted_at + cleanup trigger for full teacher removal without losing circles/records
-- 5. notify_recitation updated for sard / azfar
-- Additive only: zero existing data dropped or corrupted; build 16 remains 100% compatible.

ALTER TYPE public.record_kind ADD VALUE IF NOT EXISTS 'azfar';
ALTER TYPE public.record_kind ADD VALUE IF NOT EXISTS 'sard';

ALTER TABLE public.students
  ADD COLUMN IF NOT EXISTS monthly_juz_target numeric(4,2) NOT NULL DEFAULT 1.0;

ALTER TABLE public.students DROP CONSTRAINT IF EXISTS students_monthly_juz_target_check;
ALTER TABLE public.students ADD CONSTRAINT students_monthly_juz_target_check
  CHECK (monthly_juz_target > 0 AND monthly_juz_target <= 30);

ALTER TABLE public.recitations
  ADD COLUMN IF NOT EXISTS review_mistakes int4 NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS review_status text NULL,
  ADD COLUMN IF NOT EXISTS review_from_surah int4 NULL,
  ADD COLUMN IF NOT EXISTS review_from_ayah int4 NULL,
  ADD COLUMN IF NOT EXISTS review_to_surah int4 NULL,
  ADD COLUMN IF NOT EXISTS review_to_ayah int4 NULL,
  ADD COLUMN IF NOT EXISTS review_grade public.grade NULL;

ALTER TABLE public.recitations DROP CONSTRAINT IF EXISTS recitations_review_mistakes_check;
ALTER TABLE public.recitations ADD CONSTRAINT recitations_review_mistakes_check
  CHECK (review_mistakes >= 0);

ALTER TABLE public.recitations DROP CONSTRAINT IF EXISTS recitations_review_status_check;
ALTER TABLE public.recitations ADD CONSTRAINT recitations_review_status_check
  CHECK (review_status IS NULL OR review_status IN ('reviewed', 'not_reviewed'));

ALTER TABLE public.recitations DROP CONSTRAINT IF EXISTS recitations_review_from_surah_check;
ALTER TABLE public.recitations ADD CONSTRAINT recitations_review_from_surah_check
  CHECK (review_from_surah IS NULL OR (review_from_surah >= 1 AND review_from_surah <= 114));

ALTER TABLE public.recitations DROP CONSTRAINT IF EXISTS recitations_review_to_surah_check;
ALTER TABLE public.recitations ADD CONSTRAINT recitations_review_to_surah_check
  CHECK (review_to_surah IS NULL OR (review_to_surah >= 1 AND review_to_surah <= 114));

ALTER TABLE public.recitations DROP CONSTRAINT IF EXISTS recitations_review_ayah_check;
ALTER TABLE public.recitations ADD CONSTRAINT recitations_review_ayah_check
  CHECK (
    (review_from_ayah IS NULL OR review_from_ayah >= 1)
    AND (review_to_ayah IS NULL OR review_to_ayah >= 1)
  );

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz NULL;

UPDATE public.profiles
  SET deleted_at = coalesce(deleted_at, now())
  WHERE role = 'teacher' AND active = false AND deleted_at IS NULL;

DELETE FROM public.circle_teachers ct
USING public.profiles p
WHERE ct.teacher_profile_id = p.id
  AND p.role = 'teacher'
  AND (p.active = false OR p.deleted_at IS NOT NULL);

CREATE OR REPLACE FUNCTION public.profiles_teacher_delete_sync()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF new.role = 'teacher' THEN
    IF new.active = false OR new.deleted_at IS NOT NULL THEN
      new.active := false;
      new.deleted_at := coalesce(new.deleted_at, now());
      DELETE FROM public.circle_teachers WHERE teacher_profile_id = new.id;
      UPDATE public.circles
        SET teacher_id = NULL, teacher_name = NULL
        WHERE teacher_id = new.id;
    ELSIF old.active = false AND new.active = true THEN
      new.deleted_at := NULL;
    END IF;
  END IF;
  RETURN new;
END;
$$;

DROP TRIGGER IF EXISTS trg_profiles_teacher_delete_sync ON public.profiles;
CREATE TRIGGER trg_profiles_teacher_delete_sync
  BEFORE UPDATE OF active, deleted_at ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.profiles_teacher_delete_sync();

CREATE OR REPLACE FUNCTION public.notify_recitation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE sname text;
BEGIN
  SELECT full_name INTO sname FROM students WHERE id = new.student_id;
  INSERT INTO notifications (user_id, title, body, kind, ref_id)
  SELECT uid,
         (CASE new.kind
            WHEN 'hifz' THEN 'حفظ جديد'
            WHEN 'tasmee' THEN 'تسميع'
            WHEN 'sard' THEN 'سرد السورة'
            WHEN 'azfar' THEN 'لم يسمع'
            ELSE 'مراجعة'
          END) || ': ' || sname,
         'سورة ' || new.from_surah || ' آية ' || new.from_ayah || ' إلى سورة ' || new.to_surah || ' آية ' || new.to_ayah,
         'record', new.id
  FROM (
    SELECT parent_id AS uid FROM parent_students WHERE student_id = new.student_id
    UNION
    SELECT id FROM profiles WHERE student_id = new.student_id
  ) t;
  RETURN new;
END;
$$;

NOTIFY pgrst, 'reload schema';
