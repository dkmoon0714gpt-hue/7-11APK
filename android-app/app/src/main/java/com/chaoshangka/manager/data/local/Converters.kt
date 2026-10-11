package com.chaoshangka.manager.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * 日期型態轉換：資料庫存標準數值，UI 與 domain 使用 [LocalDate]。
 * 民國年只在顯示與解析階段處理，不會進入資料庫。
 */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }
}
