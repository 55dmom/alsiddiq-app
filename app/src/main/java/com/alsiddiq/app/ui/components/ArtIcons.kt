package com.alsiddiq.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.alsiddiq.app.R

/**
 * v3.8 (his request, Nov 8 10:15 PM «يكون داخل الزرار صور تعبر عن الزرار نفسه»): illustrated pictures inside the app's own
 * buttons and rows. Looked up by the button's title; a title with no picture keeps its Material icon. Not used in his tool modules.
 */
object ArtIcons {
    private val byTitle: Map<String, Int> = mapOf(
        "الطلاب" to R.drawable.art_students, "طلابي" to R.drawable.art_students,
        "طالب جديد" to R.drawable.art_add_student, "إضافة طالب" to R.drawable.art_add_student,
        "الحلقات" to R.drawable.art_circles, "كل الحلقات" to R.drawable.art_circles, "حلقة جديدة" to R.drawable.art_add_circle,
        "المعلمون" to R.drawable.art_teachers, "المعلمين" to R.drawable.art_teachers,
        "الحضور والغياب" to R.drawable.art_attendance, "حضور اليوم" to R.drawable.art_attendance, "تحضير اليوم" to R.drawable.art_attendance,
        "حضور وغياب المعلمين" to R.drawable.art_teacher_attendance,
        "تسجيل الآن" to R.drawable.art_record, "تسجيل التسميع" to R.drawable.art_tasmee,
        "تقرير اليوم" to R.drawable.art_reports, "التقارير والإحصائيات" to R.drawable.art_reports, "التقارير" to R.drawable.art_reports,
        "الإعلانات" to R.drawable.art_announcement, "التقارير والإعلانات" to R.drawable.art_announcement,
        "الإعدادات" to R.drawable.art_settings, "إعدادات المركز" to R.drawable.art_settings,
        "الإدارة" to R.drawable.ic_admin_shield,   // build 15: the shield with the open book, in place of the key "الأدوات" to R.drawable.art_tools, "المزيد" to R.drawable.art_more,
        "الميزانية" to R.drawable.art_budget, "الاشتراكات الشهرية" to R.drawable.art_subscriptions,
        "المتفوقون" to R.drawable.art_honors, "المكافآت" to R.drawable.art_honors,
        "وسام الشرف لهذا الأسبوع" to R.drawable.art_weekly_star,
        "عن المركز" to R.drawable.art_about, "عن التطبيق" to R.drawable.art_about,
        "تواصل مع المطوّر" to R.drawable.art_developer, "الثيمات" to R.drawable.art_themes,
        "الإشعارات" to R.drawable.art_bell, "لوحة الحلقة" to R.drawable.art_circle_photo
    )
    fun forTitle(title: String): Int? = byTitle[title.trim()]
}

/** The picture for [title] when there is one (fills most of its soft square), otherwise the Material [icon] as before. */
@androidx.compose.runtime.Composable
fun ArtOrIcon(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, box: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    val art = ArtIcons.forTitle(title)
    if (art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null,
        androidx.compose.ui.Modifier.size(box * 0.86f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    else androidx.compose.material3.Icon(icon, null, tint = tint, modifier = androidx.compose.ui.Modifier.size(iconSize))
}
