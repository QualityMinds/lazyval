package com.qualityminds.lazyval.integration.boundary.persistence

import com.qualityminds.lazyval.integration.domain.OrderDate
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import java.sql.Date

/**
 * The read-side bridge Spring Data JDBC needs for [OrderDate].
 *
 * This is the relational twin of the MongoDB date gotcha in [DateToOrderDateConverter]: the JDBC
 * driver hands a [java.sql.Date] to the converter layer for a `DATE` column, and Spring's
 * `ConversionService` does not chain converters — so the generated `Converter<LocalDate, OrderDate>`
 * is never reached and the read fails with `ConverterNotFoundException`. Bridging needs an
 * application-level decision about how a SQL date maps onto a local date, which the generator
 * cannot make.
 *
 * Only the read direction needs bridging: the generated `OrderDate -> LocalDate` write converter
 * produces a type the driver binds natively.
 *
 * Registered through `lazyval.springdata.jdbc.converters`; see the pom.
 */
@ReadingConverter
internal class SqlDateToOrderDateConverter : Converter<Date, OrderDate> {
    override fun convert(source: Date): OrderDate = OrderDate(source.toLocalDate())
}
