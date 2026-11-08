# زر «ختمتي» — أندرويد (Kotlin + Jetpack Compose library module)

تحدد كم يوم تبي تختم فيه (7، 10، 15، 30، 60… أو أي عدد) ومن أي صفحة تبدأ، ويطلع لك ورد اليوم بالصفحات والأجزاء (من سورة كذا آية كذا إلى سورة كذا آية كذا)، وتقرأ الورد داخل الزر نفسه صفحة صفحة، وتعلّم «قرأت ورد اليوم» بضغطة. فيه حلقة تقدّم ونسبة، وحالة الخطة (ماشي / متقدّم / متأخر، والورد يتوزع تلقائياً لو تأخرت عشان تلحق)، وإحصائيات، وسجل آخر الأيام، وعدد ختماتك. ومعه بيانات المصحف كاملة داخله، ما يحتاج زر المصحف.

مستقل تماماً: ما يعتمد على أي زر ثاني، ويشتغل **بدون إنترنت**، بدون حسابات وبدون تحليلات. الواجهة عربية من اليمين لليسار، والوضع الفاتح/الداكن يتبع الجهاز. minSdk 24، compileSdk 35.

## الإضافة لتطبيقك (4 خطوات)
1. فك الضغط، وانسخ مجلد `khatma` لجذر مشروعك (جنب مجلد `app`).
2. في `settings.gradle.kts` أضف:
   ```kotlin
   include(":khatma")
   ```
3. في `app/build.gradle.kts` داخل `dependencies` أضف:
   ```kotlin
   implementation(project(":khatma"))
   ```
4. تأكد إن `build.gradle.kts` اللي في جذر المشروع معرّف فيه الإضافات هذي (بنفس نسخ تطبيقك؛ جربناه على AGP 8.7.3 و Kotlin 2.0.21):
   ```kotlin
   plugins {
       id("com.android.library") version "8.7.3" apply false
       id("org.jetbrains.kotlin.android") version "2.0.21" apply false
       id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
   }
   ```
   بعدها Sync، واستخدم `KhatmaScreen()` من الحزمة `com.alsalmi.buttons.khatma`.

## فتحه من زر
```kotlin
import com.alsalmi.buttons.khatma.KhatmaScreen
import com.alsalmi.buttons.khatma.KhatmaActivity

// 1) Navigation Compose: صفحة داخل الـ NavHost حقك
composable("khatma") {
    KhatmaScreen(onBack = { navController.popBackStack() })
}
Button(onClick = { navController.navigate("khatma") }) { Text("ختمتي") }

// 2) أو Activity جاهزة (معرّفة داخل الموديول، ما تحتاج تضيفها للـ Manifest)
val context = LocalContext.current
Button(onClick = { KhatmaActivity.start(context) }) { Text("ختمتي") }
```
`onBack` اختياري: إذا عطيته يطلع سهم رجوع فوق.

## الحفظ
الخطة والتقدّم والسجل وعدد الختمات محفوظة في `SharedPreferences` باسم `alsalmi_khatma`.

## المصادر
- نص القرآن: مصحف Tanzil العثماني (Tanzil Quran Text, Uthmani, Version 1.1 — tanzil.net)، منقول حرفياً في ملف `quran-uthmani.txt` مع رأس حقوق Tanzil كما هو (رخصة Creative Commons Attribution 3.0؛ يُسمح بالنسخ والنشر بشرط عدم تعديل النص وذكر المصدر). عدد الآيات 6236، وطابقناه آية آية مع quran.com (114 سورة، 6236 من 6236 متطابقة، الفرق فقط مسافات علامات الوقف).
- البيانات (بدايات الأجزاء الـ30 والصفحات الـ604 حسب مصحف المدينة، وأسماء السور وعدد آياتها): Tanzil Quran Metadata 1.0 (tanzil.net، CC BY 3.0).
- البسملة: في نص Tanzil تجي في أول الآية الأولى من كل سورة (عدا الفاتحة والتوبة)، فنعرضها سطراً لحالها فوق السورة بنفس حروفها.
- علامات الآيات ﴿١﴾ تُضاف للعرض فقط.
- الخط: Amiri Quran (SIL Open Font License 1.1، الملف داخل المجلد).

## التحقق
تم بناؤه بـ Gradle 8.11.1 (AGP 8.7.3، Kotlin 2.0.21، Compose BOM 2024.12.01): `assembleRelease` للموديول نجح، وتطبيق تجربة يستدعي `KhatmaScreen()` و`KhatmaActivity.start()` بُني (`assembleDebug`)، وأخذنا لقطات شاشة للوضع الفاتح والداكن.
