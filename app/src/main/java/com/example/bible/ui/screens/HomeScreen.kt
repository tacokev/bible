package com.example.bible.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.bible.AppViewModel
import com.example.bible.Tab
import com.example.bible.data.Progress
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.TranslationSheet
import com.example.bible.ui.theme.Palette

@Composable
fun HomeScreen(vm: AppViewModel, progress: Progress, tab: Tab) {
    val openPicker = { vm.translationSheetOpen = true }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Path -> PathScreen(vm, progress, openPicker)
                Tab.Bible -> BibleScreen(vm, progress, openPicker)
                Tab.Search -> SearchScreen(vm, progress, openPicker)
                Tab.Profile -> ProfileScreen(vm, progress, openPicker)
            }
        }
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(AppIcons.Path, tab == Tab.Path) { vm.selectTab(Tab.Path) }
            NavItem(AppIcons.BookColor, tab == Tab.Bible) { vm.selectTab(Tab.Bible) }
            NavItem(AppIcons.Search, tab == Tab.Search) { vm.selectTab(Tab.Search) }
            NavItem(AppIcons.Person, tab == Tab.Profile) { vm.selectTab(Tab.Profile) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .then(
                if (selected) Modifier
                    .background(Palette.Selected, shape)
                    .border(2.dp, Palette.SelectedBorder, shape)
                else Modifier
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = Color.Unspecified)
    }
}
