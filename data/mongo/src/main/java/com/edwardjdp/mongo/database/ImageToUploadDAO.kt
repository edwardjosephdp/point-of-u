package com.edwardjdp.mongo.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.edwardjdp.mongo.database.entity.ImageToUpload

@Dao
interface ImageToUploadDAO {
    @Query(value = "SELECT * FROM image_to_upload_table ORDER BY id ASC")
    suspend fun getAllImages(): List<ImageToUpload>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImageToUpload(imageToUpload: ImageToUpload)

     @Query(value = "DELETE FROM image_to_upload_table WHERE id=:imageId")
    suspend fun cleanupImage(imageId: Int)

}
