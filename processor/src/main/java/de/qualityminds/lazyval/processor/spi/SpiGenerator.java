package de.qualityminds.lazyval.processor.spi;

public sealed interface SpiGenerator permits SingleFileGenerator, MultipleFilesGenerator {}
