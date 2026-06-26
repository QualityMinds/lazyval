package com.qualityminds.lazyval.testkit;

import java.nio.file.Path;

public sealed interface ApprovalDefinition{

    record File(Path path) implements ApprovalDefinition {}

    record Content(String content) implements ApprovalDefinition {}

}
