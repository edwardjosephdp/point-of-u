package com.edwardjdp.pointofuapp.data.repository

import com.edwardjdp.pointofuapp.model.Journal
import com.edwardjdp.pointofuapp.util.RequestState
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

typealias Journals = RequestState<Map<LocalDate, List<Journal>>>

interface MongoRepository {

    fun configureTheRealm()

    fun getAllJournals(): Flow<Journals>

    fun getSelectedJournal(id: ObjectId): RequestState<Journal>

}
