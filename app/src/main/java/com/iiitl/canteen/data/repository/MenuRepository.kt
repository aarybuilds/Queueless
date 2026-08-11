package com.iiitl.canteen.data.repository

import com.iiitl.canteen.data.model.MenuItem
import com.iiitl.canteen.data.remote.MenuDataSource
import kotlinx.coroutines.flow.Flow

// Thin delegation layer. No business logic yet, but it means the ViewModel
// never imports Firestore types — and if we add caching or filtering rules
// later, the ViewModel call site stays unchanged.
class MenuRepository(private val menuDataSource: MenuDataSource) {

    fun getMenuItems(cafeteriaId: String): Flow<List<MenuItem>> =
        menuDataSource.observeMenuItems(cafeteriaId)
}
