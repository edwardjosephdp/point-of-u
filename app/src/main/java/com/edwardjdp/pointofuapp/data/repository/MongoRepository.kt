package com.edwardjdp.pointofuapp.data.repository

import com.edwardjdp.pointofuapp.model.Journal
import com.edwardjdp.pointofuapp.model.RequestState
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

typealias Journals = RequestState<Map<LocalDate, List<Journal>>>

interface MongoRepository {

    fun configureTheRealm()

    fun getAllJournals(): Flow<Journals>

    fun getSelectedJournal(id: ObjectId): Flow<RequestState<Journal>>

    suspend fun insertJournal(journal: Journal): RequestState<Journal>

    suspend fun updateJournal(journal: Journal): RequestState<Journal>

    suspend fun deleteJournal(journalId: ObjectId): RequestState<Journal>

    suspend fun deleteAllJournals(): RequestState<Boolean>
}
