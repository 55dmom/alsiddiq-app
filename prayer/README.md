# زر «مواقيت الصلاة» — أندرويد (Kotlin + Jetpack Compose library module)

أوقات الصلاة الست (الفجر، الشروق، الظهر، العصر، المغرب، العشاء) محسوبة على الجهاز نفسه **بدون إنترنت**، مع الصلاة القادمة وعدّ تنازلي بالثواني، والصلاة السابقة وكم مضى عليها، ووقت الإقامة لكل صلاة (الافتراضي: الفجر 25، الظهر 20، العصر 20، المغرب 10، العشاء 20 دقيقة بعد الأذان، وتقدر تعدّلها)، والتاريخ الهجري (أم القرى) والميلادي. الحساب الافتراضي أم القرى (الفجر 18.5°، العشاء بعد المغرب بـ90 دقيقة و120 في رمضان)، ومعه 7 طرق ثانية، والعصر جمهور/حنفي. الموقع الافتراضي الطائف، وفيه قائمة مدن مدمجة فيها بحث (126 مدينة: مدن ومناطق السعودية، واليمن، والعواصم العربية، ومدن العالم)، أو موقعك بالـGPS بإذنك، أو إحداثيات يدوية. وفيه تنبيه محلي لكل صلاة تختاره (جرس جنب الصلاة)، بدون سيرفر.

مستقل تماماً: ما يعتمد على أي زر ثاني، ويشتغل **بدون إنترنت**، بدون حسابات وبدون تحليلات. الواجهة عربية من اليمين لليسار، والوضع الفاتح/الداكن يتبع الجهاز. minSdk 24، compileSdk 35.

## الإضافة لتطبيقك (4 خطوات)
1. فك الضغط، وانسخ مجلد `prayer` لجذر مشروعك (جنب مجلد `app`).
2. في `settings.gradle.kts` أضف:
   ```kotlin
   include(":prayer")
   ```
3. في `app/build.gradle.kts` داخل `dependencies` أضف:
   ```kotlin
   implementation(project(":prayer"))
   ```
4. تأكد إن `build.gradle.kts` اللي في جذر المشروع معرّف فيه الإضافات هذي (بنفس نسخ تطبيقك؛ جربناه على AGP 8.7.3 و Kotlin 2.0.21):
   ```kotlin
   plugins {
       id("com.android.library") version "8.7.3" apply false
       id("org.jetbrains.kotlin.android") version "2.0.21" apply false
       id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
   }
   ```
   بعدها Sync، واستخدم `PrayerTimesScreen()` من الحزمة `com.alsalmi.buttons.prayer`.

الأذونات (الموقع، التنبيهات، المنبّه، الإقلاع) معرّفة داخل الموديول وتندمج تلقائياً مع تطبيقك، والتطبيق يطلبها وقت الحاجة فقط. التنبيه يشتغل بـ `AlarmManager` ويعيد جدولة نفسه بعد كل صلاة وبعد إعادة تشغيل الجوال.

## فتحه من زر
```kotlin
import com.alsalmi.buttons.prayer.PrayerTimesScreen
import com.alsalmi.buttons.prayer.PrayerTimesActivity

// 1) Navigation Compose: صفحة داخل الـ NavHost حقك
composable("prayer") {
    PrayerTimesScreen(onBack = { navController.popBackStack() })
}
Button(onClick = { navController.navigate("prayer") }) { Text("مواقيت الصلاة") }

// 2) أو Activity جاهزة (معرّفة داخل الموديول، ما تحتاج تضيفها للـ Manifest)
val context = LocalContext.current
Button(onClick = { PrayerTimesActivity.start(context) }) { Text("مواقيت الصلاة") }
```
`onBack` اختياري: إذا عطيته يطلع سهم رجوع فوق.

## الحفظ
الإعدادات محفوظة في `SharedPreferences` باسم `alsalmi_prayer`.

## المصادر
- الحساب: معادلات موقع الشمس المعروفة (Jean Meeus، نفس منهج praytimes.org)، وزوايا الطرق المعتمدة. قارنّا النتيجة للطائف يوم 8 نوفمبر 2026 مع aladhan.com (أم القرى): الفجر 5:07، الشروق 6:25، الظهر 12:02، العصر 3:15، المغرب 5:39، العشاء 7:09، والفرق دقيقة أو أقل.
- التاريخ الهجري: تقويم أم القرى المدمج في النظام.
- إحداثيات المدن: قيم مركز كل مدينة (تكفي لدقة الأوقات بالدقيقة).
- ما يرسل أي شي للإنترنت.

## التحقق
تم بناؤه بـ Gradle 8.11.1 (AGP 8.7.3، Kotlin 2.0.21، Compose BOM 2024.12.01): `assembleRelease` للموديول نجح، وتطبيق تجربة يستدعي `PrayerTimesScreen()` و`PrayerTimesActivity.start()` بُني (`assembleDebug`)، وأخذنا لقطات شاشة للوضع الفاتح والداكن.
