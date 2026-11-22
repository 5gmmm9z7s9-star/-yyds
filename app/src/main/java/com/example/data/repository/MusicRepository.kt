package com.example.data.repository

import android.net.Uri
import com.example.data.importer.ImportResult
import com.example.data.importer.LocalAudioImporter
import com.example.data.local.TrackDao
import com.example.data.local.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MusicRepository(
    private val trackDao: TrackDao,
    private val importer: LocalAudioImporter,
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.observeAllTracks()

    suspend fun importAudioUris(uris: List<Uri>): ImportResult {
        val existingUris = trackDao.observeAllTracks().first().mapTo(HashSet()) { it.uriString }
        val result = importer.extractTracksFromUris(uris, existingUris)
        if (result.importedTracks.isNotEmpty()) {
            trackDao.insertTracks(result.importedTracks)
        }
        return result
    }

    suspend fun importFolderUri(treeUri: Uri): ImportResult {
        val existingUris = trackDao.observeAllTracks().first().mapTo(HashSet()) { it.uriString }
        val result = importer.extractTracksFromTreeUri(treeUri, existingUris)
        if (result.importedTracks.isNotEmpty()) {
            trackDao.insertTracks(result.importedTracks)
        }
        return result
    }

    suspend fun getTrackByUri(uriString: String): TrackEntity? = trackDao.getTrackByUri(uriString)

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.updateFavorite(trackId, isFavorite)
    }

    suspend fun markPlayed(trackId: Long) {
        trackDao.updateLastPlayed(trackId, System.currentTimeMillis())
    }

    suspend fun removeTrack(trackId: Long) {
        trackDao.deleteTrackById(trackId)
    }

    suspend fun clearLibrary() {
        trackDao.clearAllTracks()
    }
}
