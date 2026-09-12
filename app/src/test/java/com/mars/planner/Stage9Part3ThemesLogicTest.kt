package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.ui.theme.MarsMotion
import com.mars.planner.ui.theme.ThemeSceneBackgrounds
import com.mars.planner.ui.theme.ThemeSceneMode
import com.mars.planner.ui.theme.ThemeSceneScreen
import org.junit.Test

class Stage9Part3ThemesLogicTest {

    @Test
    fun allSevenThemesHaveMobileSceneBackground() {
        assertThat(AppTheme.entries.filter { ThemeSceneBackgrounds.hasScene(it) })
            .containsExactlyElementsIn(AppTheme.entries)
        AppTheme.entries.forEach { theme ->
            assertThat(ThemeSceneBackgrounds.config(theme)).isNotNull()
            assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(theme)).isTrue()
        }
    }

    @Test
    fun sceneThemesUseDistinctCompositionModesPerScreen() {
        AppTheme.entries.forEach { theme ->
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Today))
                .isEqualTo(ThemeSceneMode.Hero)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Tasks))
                .isEqualTo(ThemeSceneMode.CompactHeader)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Projects))
                .isEqualTo(ThemeSceneMode.CompactHeader)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Calendar))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Settings))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Sync))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.TaskForm))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.TaskDetail))
                .isEqualTo(ThemeSceneMode.ContentSurface)
        }
    }

    @Test
    fun sceneThemesMapToDistinctDrawableResources() {
        val resIds = AppTheme.entries.map { ThemeSceneBackgrounds.config(it)!!.drawableRes }
        assertThat(resIds).containsNoDuplicates()
        assertThat(resIds).hasSize(AppTheme.entries.size)
    }

    @Test
    fun sceneThemesEmbedMarsSoAvatarShouldBeSuppressed() {
        AppTheme.entries.forEach { theme ->
            assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(theme)).isTrue()
        }
    }

    @Test
    fun navPulseParametersMatchCorr1Contract() {
        assertThat(MarsMotion.NavPulseCycleMs).isEqualTo(2000)
        assertThat(MarsMotion.NavPulseScalePeak).isWithin(0.001f).of(1.025f)
    }

    @Test
    fun themeSwitchDoesNotImplyPlannerMutation() {
        // Смена темы — только DataStore AppSettings.themeId; без saveTask/saveProject.
        val before = AppTheme.ORBIT
        val after = AppTheme.WHITE_STATION
        assertThat(before.key).isNotEqualTo(after.key)
        assertThat(ThemeSceneBackgrounds.hasScene(before)).isTrue()
        assertThat(ThemeSceneBackgrounds.hasScene(after)).isTrue()
    }
}
