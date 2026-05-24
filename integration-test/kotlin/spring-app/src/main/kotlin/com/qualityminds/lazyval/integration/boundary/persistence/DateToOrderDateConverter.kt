package com.qualityminds.lazyval.integration.boundary.persistence

import com.qualityminds.lazyval.integration.domain.OrderDate
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import java.time.ZoneOffset
import java.util.*

@ReadingConverter
internal class DateToOrderDateConverter : Converter<Date, OrderDate> {
    override fun convert(source: Date): OrderDate =
        OrderDate(source.toInstant().atZone(ZoneOffset.UTC).toLocalDate())
}
