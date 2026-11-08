# زر «المصحف» — أندرويد (Kotlin + Jetpack Compose library module)

المصحف كامل (114 سورة، 604 صفحة برسم المصحف العثماني) تقرأه صفحة صفحة وتقلّب بالسحب مثل المصحف الورقي، مع فهرس للسور وبحث بالاسم أو الرقم، وقائمة الأجزاء الـ30، والانتقال لأي صفحة برقمها أو بشريط التمرير، وتكبير وتصغير الخط، و«تابع القراءة» من آخر صفحة وقفت عندها تلقائياً، وعلامة تحفظها بنفسك. الخط القرآني Amiri Quran مدمج. وتقدر تفتحه على صفحة معيّنة مباشرة: `MushafScreen(startPage = 50)`.

مستقل تماماً: ما يعتمد على أي زر ثاني، ويشتغل **بدون إنترنت**، بدون حسابات وبدون تحليلات. الواجهة عربية من اليمين لليسار، والوضع الفاتح/الداكن يتبع الجهاز. minSdk 24، compileSdk 35.

## الإضافة لتطبيقك (4 خطوات)
1. فك الضغط، وانسخ مجلد `mushaf` لجذر مشروعك (جنب مجلد `app`).
2. في `settings.gradle.kts` أضف:
   ```kotlin
   include(":mushaf")
   ```
3. في `app/build.gradle.kts` داخل `dependencies` أضف:
   ```kotlin
   implementation(project(":mushaf"))
   ```
4. تأكد إن `build.gradle.kts` اللي في جذر المشروع معرّف فيه الإضافات هذي (بنفس نسخ تطبيقك؛ جربناه على AGP 8.7.3 و Kotlin 2.0.21):
   ```kotlin
   plugins {
       id("com.android.library") version "8.7.3" apply false
       id("org.jetbrains.kotlin.android") version "2.0.21" apply false
       id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
   }
   ```
   بعدها Sync، واستخدم `MushafScreen()` من الحزمة `com.alsalmi.buttons.mushaf`.

## فتحه من زر
```kotlin
import com.alsalmi.buttons.mushaf.MushafScreen
import com.alsalmi.buttons.mushaf.MushafActivity

// 1) Navigation Compose: صفحة داخل الـ NavHost حقك
composable("mushaf") {
    MushafScreen(onBack = { navController.popBackStack() })
}
Button(onClick = { navController.navigate("mushaf") }) { Text("المصحف") }

// 2) أو Activity جاهزة (معرّفة داخل الموديول، ما تحتاج تضيفها للـ Manifest)
val context = LocalContext.current
Button(onClick = { MushafActivity.start(context) }) { Text("المصحف") }
```
`onBack` اختياري: إذا عطيته يطلع سهم رجوع فوق.

## الحفظ
آخر صفحة قرأتها، والعلامة، وحجم الخط محفوظة في `SharedPreferences` باسم `alsalmi_mushaf`.

## المصادر
- نص القرآن: مصحف Tanzil العثماني (Tanzil Quran Text, Uthmani, Version 1.1 — tanzil.net)، منقول حرفياً في ملف `quran-uthmani.txt` مع رأس حقوق Tanzil كما هو (رخصة Creative Commons Attribution 3.0؛ يُسمح بالنسخ والنشر بشرط عدم تعديل النص وذكر المصدر). عدد الآيات 6236، وطابقناه آية آية مع quran.com (114 سورة، 6236 من 6236 متطابقة، الفرق فقط مسافات علامات الوقف).
- البيانات (بدايات الأجزاء الـ30 والصفحات الـ604 حسب مصحف المدينة، وأسماء السور وعدد آياتها): Tanzil Quran Metadata 1.0 (tanzil.net، CC BY 3.0).
- البسملة: في نص Tanzil تجي في أول الآية الأولى من كل سورة (عدا الفاتحة والتوبة)، فنعرضها سطراً لحالها فوق السورة بنفس حروفها.
- علامات الآيات ﴿١﴾ تُضاف للعرض فقط.
- الخط: Amiri Quran (SIL Open Font License 1.1، الملف داخل المجلد).

## التحقق
تم بناؤه بـ Gradle 8.11.1 (AGP 8.7.3، Kotlin 2.0.21، Compose BOM 2024.12.01): `assembleRelease` للموديول نجح، وتطبيق تجربة يستدعي `MushafScreen()` و`MushafActivity.start()` بُني (`assembleDebug`)، وأخذنا لقطات شاشة للوضع الفاتح والداكن.
