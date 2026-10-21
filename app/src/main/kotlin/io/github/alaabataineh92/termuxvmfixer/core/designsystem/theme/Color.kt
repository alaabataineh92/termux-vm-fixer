package io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

val LightPrimary = Color(0xFF2E6B4F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFB2F0CE)
val LightOnPrimaryContainer = Color(0xFF002113)
val LightInversePrimary = Color(0xFF97D4B3)

val LightSecondary = Color(0xFF4E6355)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD1E8D6)
val LightOnSecondaryContainer = Color(0xFF0B1F14)

val LightTertiary = Color(0xFF3C6472)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFBFE9FA)
val LightOnTertiaryContainer = Color(0xFF001F29)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF6FBF4)
val LightOnBackground = Color(0xFF171D19)

val LightSurface = Color(0xFFF6FBF4)
val LightOnSurface = Color(0xFF171D19)
val LightSurfaceVariant = Color(0xFFDCE5DC)
val LightOnSurfaceVariant = Color(0xFF464F49)
val LightSurfaceTint = LightPrimary

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F6EF)
val LightSurfaceContainer = Color(0xFFEAF1E9)
val LightSurfaceContainerHigh = Color(0xFFE4ECE3)
val LightSurfaceContainerHighest = Color(0xFFDEE6DD)
val LightSurfaceDim = Color(0xFFD7DED6)
val LightSurfaceBright = Color(0xFFF6FBF4)

val LightOutline = Color(0xFF707973)
val LightOutlineVariant = Color(0xFFBFC9C1)

val LightInverseSurface = Color(0xFF2C322D)
val LightInverseOnSurface = Color(0xFFEDF1EB)
val LightScrim = Color(0xFF000000)

val DarkPrimary = Color(0xFF97D4B3)
val DarkOnPrimary = Color(0xFF003824)
val DarkPrimaryContainer = Color(0xFF125138)
val DarkOnPrimaryContainer = Color(0xFFB2F0CE)
val DarkInversePrimary = Color(0xFF2E6B4F)

val DarkSecondary = Color(0xFFB5CCBB)
val DarkOnSecondary = Color(0xFF213528)
val DarkSecondaryContainer = Color(0xFF374B3E)
val DarkOnSecondaryContainer = Color(0xFFD1E8D6)

val DarkTertiary = Color(0xFFA4CDDE)
val DarkOnTertiary = Color(0xFF043541)
val DarkTertiaryContainer = Color(0xFF234C59)
val DarkOnTertiaryContainer = Color(0xFFBFE9FA)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0F1511)
val DarkOnBackground = Color(0xFFDEE4DD)

val DarkSurface = Color(0xFF0F1511)
val DarkOnSurface = Color(0xFFDEE4DD)
val DarkSurfaceVariant = Color(0xFF404943)
val DarkOnSurfaceVariant = Color(0xFFBFC9C1)
val DarkSurfaceTint = DarkPrimary

val DarkSurfaceContainerLowest = Color(0xFF090E0B)
val DarkSurfaceContainerLow = Color(0xFF171D19)
val DarkSurfaceContainer = Color(0xFF1B211D)
val DarkSurfaceContainerHigh = Color(0xFF262C28)
val DarkSurfaceContainerHighest = Color(0xFF313733)
val DarkSurfaceDim = Color(0xFF0F1511)
val DarkSurfaceBright = Color(0xFF353B36)

val DarkOutline = Color(0xFF8A938C)
val DarkOutlineVariant = Color(0xFF404943)

val DarkInverseSurface = Color(0xFFDEE4DD)
val DarkInverseOnSurface = Color(0xFF2C322D)
val DarkScrim = Color(0xFF000000)

data class StatusTone(val container: Color, val onContainer: Color)

object StatusColors {

    val SuccessLight = StatusTone(Color(0xFF2E7D32), Color(0xFFFFFFFF))
    val WarningLight = StatusTone(Color(0xFFB45309), Color(0xFFFFFFFF))
    val NeutralLight = StatusTone(Color(0xFF5C6660), Color(0xFFFFFFFF))

    val SuccessDark = StatusTone(Color(0xFF8FDFA0), Color(0xFF04350D))
    val WarningDark = StatusTone(Color(0xFFFFC46B), Color(0xFF3A1D00))
    val NeutralDark = StatusTone(Color(0xFFC3CCC6), Color(0xFF1F2621))
}
