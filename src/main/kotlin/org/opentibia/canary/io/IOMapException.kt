package org.opentibia.canary.io

/**
 * Port of `IOMapException` (src/io/iomap.hpp:159-169), thrown by the map
 * loader when an OTBM node cannot be read.
 */
class IOMapException(message: String) : Exception(message)
