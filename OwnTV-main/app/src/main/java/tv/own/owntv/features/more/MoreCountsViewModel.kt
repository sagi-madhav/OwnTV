package tv.own.owntv.features.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice

/** How many channels, films and shows a list holds, one number per type. */
data class TypeCounts(val live: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val total: Int get() = live + movies + series
}

/** One row of the Favourites / History pane list: what it is called and which type it is. */
data class PaneItem(val name: String, val type: MediaType)

/** How many of each type the pane lists. Enough to be useful, short enough to stay a preview. */
private const val PANE_ITEMS = 5

/**
 * What the Local sync pane reports: whether this device is reachable, and who it is paired with.
 *
 * [listening] is the whole point of the pane. Plan 4 Phase 12's own finding was that reaching a
 * device requires that device to be listening, and "Sync mode" is what makes that visible — so the
 * hub answers "why can't my phone see it" without opening anything.
 */
data class SyncSnapshot(val listening: Boolean = false, val devices: List<PairedDevice> = emptyList())

/**
 * The counts the More rows and the two new screens' tabs carry.
 *
 * Every one of these queries already exists on the DAOs — `countFavorites` and `countHistory`, both
 * joined to the content table so a favourite whose channel went stale on a re-sync is not counted
 * before the relink purges it. Nothing here is a new query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreCountsViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
    localSync: LocalSyncManager,
) : ViewModel() {

    /**
     * Local sync, read-only.
     *
     * Deliberately the **manager**, not `LocalSyncViewModel`: that one owns `startHosting` /
     * `stopHosting`, and on the television `koinViewModel()` scopes to the Activity, so sharing it
     * with the hub would be Plan Z defect 6 again — one object with two opinions about whether this
     * device should be advertising. The pane reports; the screen decides.
     */
    val sync: StateFlow<SyncSnapshot> = combine(
        localSync.hostState,
        localSync.pairedDevices,
    ) { host, devices ->
        SyncSnapshot(listening = host is CompanionServerState.Listening, devices = devices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSnapshot())

    /** The last successful backup, or `null` if none has been taken on this device. */
    val lastBackup: StateFlow<SettingsRepository.LastBackup?> = settings.lastBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    val favorites: StateFlow<TypeCounts> = counts(favorites = true)
    val history: StateFlow<TypeCounts> = counts(favorites = false)

    /**
     * The names behind the counts, for the Favourites and History panes — **read-only, and never a
     * browse view model.**
     *
     * Plan Z defect 6 is the reason this is a plain snapshot query and not the pinned pane: the TV
     * app has one Activity and no nav graph, so `koinViewModel()` hands out one shared instance and
     * a pane borrowing `MoviesScreen` would fight the destination over which folder is pinned. These
     * DAO calls own no state at all.
     *
     * Re-queried whenever the counts change, which is what makes a snapshot behave: star something
     * and the count flow ticks, so the list is rebuilt with it.
     */
    val favoriteItems: StateFlow<List<PaneItem>> = items(favorites = true)
    val historyItems: StateFlow<List<PaneItem>> = items(favorites = false)

    private fun items(favorites: Boolean): StateFlow<List<PaneItem>> =
        combine(ctx, if (favorites) this.favorites else this.history) { c, _ -> c }
            .flatMapLatest { c ->
                flow {
                    if (c.profileId < 0) {
                        emit(emptyList())
                        return@flow
                    }
                    fun ids(type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }
                    val key = ContentOrderEntity.FAV_CONTEXT
                    emit(
                        buildList {
                            if (favorites) {
                                channelDao.snapshotFavoritesManual(c.profileId, key, ids(MediaType.LIVE), PANE_ITEMS)
                                    .forEach { add(PaneItem(it.name, MediaType.LIVE)) }
                                movieDao.snapshotFavoritesManual(c.profileId, key, ids(MediaType.MOVIE), PANE_ITEMS)
                                    .forEach { add(PaneItem(it.name, MediaType.MOVIE)) }
                                seriesDao.snapshotFavoritesManual(c.profileId, key, ids(MediaType.SERIES), PANE_ITEMS)
                                    .forEach { add(PaneItem(it.name, MediaType.SERIES)) }
                            } else {
                                channelDao.recentlyWatchedWithTimestampFiltered(c.profileId, ids(MediaType.LIVE), PANE_ITEMS)
                                    .first().forEach { add(PaneItem(it.channel.name, MediaType.LIVE)) }
                                movieDao.recentlyWatchedSnapshot(c.profileId, ids(MediaType.MOVIE), PANE_ITEMS)
                                    .forEach { add(PaneItem(it.name, MediaType.MOVIE)) }
                                seriesDao.recentlyWatchedSnapshot(c.profileId, ids(MediaType.SERIES), PANE_ITEMS)
                                    .forEach { add(PaneItem(it.name, MediaType.SERIES)) }
                            }
                        },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun counts(favorites: Boolean): StateFlow<TypeCounts> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(TypeCounts())
            } else {
                // An empty id list makes the IN clause match nothing useful — the sentinel every
                // other count flow in the app uses.
                fun ids(type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }
                combine(
                    if (favorites) channelDao.countFavorites(c.profileId, ids(MediaType.LIVE))
                    else channelDao.countHistory(c.profileId, ids(MediaType.LIVE)),
                    if (favorites) movieDao.countFavorites(c.profileId, ids(MediaType.MOVIE))
                    else movieDao.countHistory(c.profileId, ids(MediaType.MOVIE)),
                    if (favorites) seriesDao.countFavorites(c.profileId, ids(MediaType.SERIES))
                    else seriesDao.countHistory(c.profileId, ids(MediaType.SERIES)),
                    ::TypeCounts,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeCounts())
}
