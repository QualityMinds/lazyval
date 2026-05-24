package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.domain.OrderDate
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import java.time.ZoneOffset
import java.util.Date

@WritingConverter
class OrderDateToDateConverter : Converter<OrderDate, Date> {
    override fun convert(source: OrderDate): Date =
        Date.from(source.value.atStartOfDay(ZoneOffset.UTC).toInstant())
}
