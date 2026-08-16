package dev.sift.app.ui.grid

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.data.db.MediaAssetDao
import dev.sift.data.db.MediaAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The thumbnail grid (M0).
 *
 * §13 budgets 60fps sustained over 5000 items. Two things carry that: rows come
 * from Room in fixed pages rather than the whole library at once, and Coil is
 * handed the MediaStore URI directly so it can use the system thumbnail cache
 * instead of decoding full-size frames off the main thread.
 */
@HiltViewModel
class GridViewModel @Inject constructor(
    private val mediaAssets: MediaAssetDao,
) : ViewModel() {

    private val internal = MutableStateFlow<List<MediaAsset>>(emptyList())
    val assets: StateFlow<List<MediaAsset>> = internal.asStateFlow()

    private var offset = 0
    private var exhausted = false

    init {
        loadMore()
    }

    fun loadMore() {
        if (exhausted) return
        viewModelScope.launch {
            val page = mediaAssets.page(PAGE, offset)
            if (page.isEmpty()) {
                exhausted = true
                return@launch
            }
            offset += page.size
            internal.value = internal.value + page
        }
    }

    private companion object {
        const val PAGE = 200
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(onBack: () -> Unit, viewModel: GridViewModel = hiltViewModel()) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${assets.size} photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(assets, key = { it.id }) { asset ->
                AsyncImage(
                    model = asset.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).padding(1.dp),
                )
            }
            item {
                // Paging on reaching the tail keeps the first frame cheap; the
                // §13 cold-start budget is 1.5s to a usable deck.
                //
                // Inside LaunchedEffect, not called directly: a bare call here
                // would run during composition, and since it mutates the state
                // this grid reads from, each load would schedule another
                // recomposition that loads again. Keyed on the current size so
                // it fires once per page rather than once per frame.
                LaunchedEffect(assets.size) { viewModel.loadMore() }
            }
        }
    }
}
