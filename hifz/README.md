# زر «وضع الحفظ» — أندرويد (Kotlin + Jetpack Compose library module)

وضع حفظ للمصحف **كامل** (114 سورة، 6236 آية): تختار أي سورة وأي مقطع (من آية إلى آية)، وتحفظ بثلاث طرق: قراءة، أو إخفاء الآيات (تضغط وتطلع الآية)، أو اختبار الكلمات (تطلع كلمة كلمة لين تكمل الآية). لكل آية عدّاد تكرار بحلقة (هدف 3/5/7/10/20)، وزر «حفظتها» وزر «ضعيفة». تبويب «تقدّمي» يعرض محفوظك لكل سورة والمجموع من 6236 مع بحث، وتبويب «المراجعة» يجمع الآيات الضعيفة وتختبر نفسك فيها لحالها، و«المقطع التالي» ينقلك للخمس آيات اللي بعدها.

مستقل تماماً: ما يعتمد على أي زر ثاني، ويشتغل **بدون إنترنت**، بدون حسابات وبدون تحليلات. الواجهة عربية من اليمين لليسار، والوضع الفاتح/الداكن يتبع الجهاز. minSdk 24، compileSdk 35.

## الإضافة لتطبيقك (4 خطوات)
1. فك الضغط، وانسخ مجلد `hifz` لجذر مشروعك (جنب مجلد `app`).
2. في `settings.gradle.kts` أضف:
   ```kotlin
   include(":hifz")
   ```
3. في `app/build.gradle.kts` داخل `dependencies` أضف:
   ```kotlin
   implementation(project(":hifz"))
   ```
4. تأكد إن `build.gradle.kts` اللي في جذر المشروع معرّف فيه الإضافات هذي (بنفس نسخ تطبيقك؛ جربناه على AGP 8.7.3 و Kotlin 2.0.21):
   ```kotlin
   plugins {
       id("com.android.library") version "8.7.3" apply false
       id("org.jetbrains.kotlin.android") version "2.0.21" apply false
       id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
   }
   ```
   بعدها Sync، واستخدم `HifzScreen()` من الحزمة `com.alsalmi.buttons.hifz`.

## فتحه من زر
```kotlin
import com.alsalmi.buttons.hifz.HifzScreen
import com.alsalmi.buttons.hifz.HifzActivity

// 1) Navigation Compose: صفحة داخل الـ NavHost حقك
composable("hifz") {
    HifzScreen(onBack = { navController.popBackStack() })
}
Button(onClick = { navController.navigate("hifz") }) { Text("وضع الحفظ") }

// 2) أو Activity جاهزة (معرّفة داخل الموديول، ما تحتاج تضيفها للـ Manifest)
val context = LocalContext.current
Button(onClick = { HifzActivity.start(context) }) { Text("وضع الحفظ") }
```
`onBack` اختياري: إذا عطيته يطلع سهم رجوع فوق.

## الحفظ
المحفوظ، والآيات الضعيفة، وعدّادات التكرار، وآخر سورة ومقطع، وحجم الخط محفوظة في `SharedPreferences` باسم `alsalmi_hifz`.

## المصادر
- نص القرآن: مصحف Tanzil العثماني (Tanzil Quran Text, Uthmani, Version 1.1 — tanzil.net)، منقول حرفياً في ملف `quran-uthmani.txt` مع رأس حقوق Tanzil كما هو (رخصة Creative Commons Attribution 3.0؛ يُسمح بالنسخ والنشر بشرط عدم تعديل النص وذكر المصدر). عدد الآيات 6236، وطابقناه آية آية مع quran.com (114 سورة، 6236 من 6236 متطابقة، الفرق فقط مسافات علامات الوقف).
- البيانات (بدايات الأجزاء الـ30 والصفحات الـ604 حسب مصحف المدينة، وأسماء السور وعدد آياتها): Tanzil Quran Metadata 1.0 (tanzil.net، CC BY 3.0).
- البسملة: في نص Tanzil تجي في أول الآية الأولى من كل سورة (عدا الفاتحة والتوبة)، فنعرضها سطراً لحالها فوق السورة بنفس حروفها.
- علامات الآيات ﴿١﴾ تُضاف للعرض فقط.
- الخط: Amiri Quran (SIL Open Font License 1.1، الملف داخل المجلد).

## التحقق
تم بناؤه بـ Gradle 8.11.1 (AGP 8.7.3، Kotlin 2.0.21، Compose BOM 2024.12.01): `assembleRelease` للموديول نجح، وتطبيق تجربة يستدعي `HifzScreen()` و`HifzActivity.start()` بُني (`assembleDebug`)، وأخذنا لقطات شاشة للوضع الفاتح والداكن.
