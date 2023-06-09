package com.edwardjdp.mongo.repository

import android.annotation.SuppressLint
import com.edwardjdp.util.model.Journal
import com.edwardjdp.util.model.RequestState
import com.edwardjdp.util.Constants.APP_ID
import com.edwardjdp.util.toInstant
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.mongodb.kbson.ObjectId
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object MongoDB : MongoRepository {

    private val app = App.create(APP_ID)
    private val user = app.currentUser
    private lateinit var realm: Realm

    init {
        configureTheRealm()
    }

    override fun configureTheRealm() {
        if (user != null) {
            val config = SyncConfiguration
                .Builder(
                    user = user,
                    schema = setOf(Journal::class),
                )
                .initialSubscriptions { sub ->
                    add(
                        query = sub.query<Journal>(
                            query = "ownerId == $0",
                            user.id
                        ),
                        name = "User's Journals"
                    )
                }
                .log(level = LogLevel.ALL)
                .build()

            realm = Realm.open(configuration = config)
        }
    }

    @SuppressLint("NewApi")
    override fun getAllJournals(): Flow<Journals> {
        return if (user != null) {
            try {
                realm.query<Journal>(
                    query = "ownerId == $0",
                    user.id
                )
                    .sort(property = "date", sortOrder = Sort.DESCENDING)
                    .asFlow()
                    .map { result ->
                        RequestState.Success(
                            data = result.list.groupBy {
                                it.date.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                        )
                    }
            } catch (e: java.lang.Exception) {
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserAuthenticationError())) }
        }
    }

    @SuppressLint("NewApi")
    override fun getFilteredJournals(zonedDateTime: ZonedDateTime): Flow<Journals> {
        return if (user != null) {
            try {
                realm.query<Journal>(
                    "ownerId == $0 AND date < $1 AND date > $2",
                    user.id,
                    RealmInstant.from(
                        LocalDateTime.of(
                            zonedDateTime.toLocalDate().plusDays(1),
                            LocalTime.MIDNIGHT
                        ).toEpochSecond(zonedDateTime.offset),
                        nanosecondAdjustment = 0
                    ),
                    RealmInstant.from(
                        LocalDateTime.of(
                            zonedDateTime.toLocalDate(),
                            LocalTime.MIDNIGHT
                        ).toEpochSecond(zonedDateTime.offset),
                        nanosecondAdjustment = 0
                    ),
                ).asFlow().map { result ->
                    RequestState.Success(
                        data = result.list.groupBy {
                            it.date
                                .toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                    )
                }
            } catch (e: java.lang.Exception) {
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserAuthenticationError())) }
        }
    }

    override fun getSelectedJournal(id: ObjectId): Flow<RequestState<Journal>> {
        return if (user != null) {
            try {
                realm.query<Journal>(query = "_id == $0", id).asFlow().map {
                    RequestState.Success(data = it.list.first())
                }
            } catch (e: Exception) {
                flow { emit(RequestState.Error(e)) }
            }
        } else {
            flow { emit(RequestState.Error(UserAuthenticationError())) }
        }
    }

    override suspend fun insertJournal(journal: Journal): RequestState<Journal> {
        return if (user != null) {
            realm.write {
                try {
                    val addedJournal = copyToRealm(journal.apply { ownerId = user.id })
                    RequestState.Success(data = addedJournal)
                } catch (e: Exception) {
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserAuthenticationError())
        }
    }

    override suspend fun updateJournal(journal: Journal): RequestState<Journal> {
        return if (user != null) {
            realm.write {
                val queriedJournal = query<Journal>(query = "_id == $0", journal._id).first().find()
                if (queriedJournal != null) {
                    queriedJournal.title = journal.title
                    queriedJournal.description = journal.description
                    queriedJournal.mood = journal.mood
                    queriedJournal.images = journal.images
                    queriedJournal.date = journal.date
                    RequestState.Success(data = queriedJournal)
                } else {
                    RequestState.Error(error = Throwable("Queried journal not found."))
                }
            }
        } else {
            RequestState.Error(UserAuthenticationError())
        }
    }

    override suspend fun deleteJournal(journalId: ObjectId): RequestState<Journal> {
        return if (user != null) {
            realm.write {
                val queriedJournal = query<Journal>(
                    query = "_id == $0 AND ownerId == $1", journalId, user.id
                ).first().find()

                if (queriedJournal != null) {
                    try {
                        delete(queriedJournal)
                        RequestState.Success(data = queriedJournal)
                    } catch (e: Exception) {
                        RequestState.Error(e)
                    }
                } else {
                    RequestState.Error(error = Throwable("Queried journal not found."))
                }
            }
        } else {
            RequestState.Error(UserAuthenticationError())
        }
    }

    override suspend fun deleteAllJournals(): RequestState<Boolean> {
        return if (user != null) {
            realm.write {
                val journals = this.query<Journal>("ownerId == $0", user.id).find()
                try {
                    delete(journals)
                    RequestState.Success(data = true)
                } catch (e: Exception) {
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserAuthenticationError())
        }
    }
}

private class UserAuthenticationError(): Exception("User is not logged in.")
