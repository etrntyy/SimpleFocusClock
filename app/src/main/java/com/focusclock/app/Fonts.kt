package com.focusclock.app

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** The clock faces offered in Settings. Variable fonts are pinned to [weight]; static ones ignore it. */
@OptIn(ExperimentalTextApi::class)
class ClockFont(res: Int, weight: Int = 400) {
    val family = FontFamily(
        Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    )
}

val ClockFonts = listOf(
    ClockFont(R.font.f01_poppins, 200),
    ClockFont(R.font.f02_orbitron, 500),
    ClockFont(R.font.f03_oswald, 300),
    ClockFont(R.font.f04_share_tech_mono),
    ClockFont(R.font.f05_bebas_neue),
    ClockFont(R.font.f06_roboto_mono, 300),
    ClockFont(R.font.f07_major_mono),
    ClockFont(R.font.f08_audiowide),
    ClockFont(R.font.f09_rajdhani, 300),
    ClockFont(R.font.f10_teko, 300),
    ClockFont(R.font.f11_exo2, 200),
    ClockFont(R.font.f12_michroma),
    ClockFont(R.font.f13_syncopate),
    ClockFont(R.font.f14_space_mono),
    ClockFont(R.font.f15_bungee),
    ClockFont(R.font.f16_righteous),
    ClockFont(R.font.f17_anton),
    ClockFont(R.font.f18_quicksand, 300),
    ClockFont(R.font.f19_montserrat, 200),
    ClockFont(R.font.f20_playfair),
    ClockFont(R.font.f21_cormorant, 300),
    ClockFont(R.font.f22_dm_serif),
    ClockFont(R.font.f23_press_start),
    ClockFont(R.font.f24_vt323),
    ClockFont(R.font.f25_silkscreen),
    ClockFont(R.font.f26_monoton),
    ClockFont(R.font.f27_dancing_script),
    ClockFont(R.font.f28_lobster),
)
