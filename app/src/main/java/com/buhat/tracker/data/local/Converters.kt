package com.buhat.tracker.data.local

import androidx.room.TypeConverter
import com.buhat.tracker.domain.SetType

class Converters {

    @TypeConverter
    fun setTypeToString(value: SetType?): String? = value?.name

    @TypeConverter
    fun stringToSetType(value: String?): SetType? = value?.let { SetType.valueOf(it) }

    @TypeConverter
    fun statusToString(value: WorkoutStatus?): String? = value?.name

    @TypeConverter
    fun stringToStatus(value: String?): WorkoutStatus? = value?.let { WorkoutStatus.valueOf(it) }
}
