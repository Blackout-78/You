package com.you.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The gray search bar under the title of Music, Movies and TV Shows. Tapping it opens the search screen. */
@Composable
fun GraySearchBar(vm: AppViewModel) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(DarkGray)
            .clickable { vm.open(Screen.Search) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { Text("Search songs, albums, artists, movies, shows", color = Muted, fontSize = 14.sp, maxLines = 1) }
}
