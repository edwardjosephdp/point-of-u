package com.edwardjdp.pointofuapp.data.repository

import com.edwardjdp.pointofuapp.model.Journal
import com.edwardjdp.pointofuapp.util.Constants.APP_ID
import com.edwardjdp.pointofuapp.util.RequestState
import com.edwardjdp.pointofuapp.util.toInstant
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

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
}


private class UserAuthenticationError(): Exception("User is not logged in.")
