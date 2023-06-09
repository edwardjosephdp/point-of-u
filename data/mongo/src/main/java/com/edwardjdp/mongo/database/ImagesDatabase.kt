package com.edwardjdp.mongo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.edwardjdp.mongo.database.entity.ImageToDelete
import com.edwardjdp.mongo.database.entity.ImageToUpload

@Database(
    entities = [ImageToUpload::class, ImageToDelete::class],
    version = 2,
    exportSchema = false,
)
abstract class ImagesDatabase: RoomDatabase() {
    abstract fun imageToUploadDao(): ImageToUploadDAO
    abstract fun imageToDeleteDao(): ImageToDeleteDAO
}
