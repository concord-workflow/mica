package ca.ibodrov.mica.server.data.meta;

import java.util.List;

public record ConcordFileMetadata(String relativePath, List<FlowMetadata> flows, List<ScannerWarning> warnings) {
}
