package com.qualityminds.lazyval.integration.boundary.rest

import jakarta.ws.rs.ApplicationPath
import jakarta.ws.rs.core.Application

@ApplicationPath("/")
open class JaxRsApplication : Application()