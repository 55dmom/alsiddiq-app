# زر «تواصل مع المطوّر» — أندرويد (Kotlin + Jetpack Compose library module)

صفحة تواصل بنفس بيانات «تواصل معنا» في تطبيق تفاؤل: أيقونة الطائرة الورقية بلون تيليجرام، والنص «راسلنا على قناة تفاؤل في تيليجرام: اقتراح، سؤال، أو استفسار.»، وزر «افتح قناة تفاؤل في تيليجرام» يفتح https://t.me/tafaulapp (في تطبيق تيليجرام أو المتصفح)، وتحته الرابط t.me/tafaulapp، ومعه زرين صغار: نسخ الرابط ومشاركته. ما يُحفظ أي شي، وبدون حسابات أو نماذج أو صلاحيات. يدعم الفاتح والداكن، والواجهة من اليمين لليسار. minSdk 24.

## طريقة الإضافة (4 خطوات)

1. فك الضغط وانسخ مجلد `contact` لجذر مشروعك (جنب مجلد `app`).
2. في `settings.gradle.kts` أضف:
   ```kotlin
   include(":contact")
   ```
3. في `app/build.gradle.kts` داخل `dependencies` أضف:
   ```kotlin
   implementation(project(":contact"))
   ```
   (المشروع لازم يكون فيه Compose و Kotlin Compose plugin مثل أي مشروع Compose حديث.)
4. اسوِ **Sync** وافتح الصفحة من زرك بأحد الطريقتين تحت.

## فتحه كصفحة مستقلة (Activity جاهزة)

```kotlin
import com.alsalmi.buttons.contact.ContactDeveloperActivity

Button(onClick = { ContactDeveloperActivity.start(context) }) {
    Text("تواصل مع المطوّر")
}
```

## أو داخل شاشاتك (Composable)

```kotlin
import com.alsalmi.buttons.contact.ContactDeveloperScreen

var showContact by remember { mutableStateOf(false) }
if (showContact) {
    ContactDeveloperScreen(onBack = { showContact = false })
} else {
    Button(onClick = { showContact = true }) { Text("تواصل مع المطوّر") }
}
```

> تم فحصه: `assembleRelease` للموديول ناجح، وتطبيق تجريبي يستخدمه ناجح، وصور الشاشة بالوضع الفاتح والداكن سليمة.
