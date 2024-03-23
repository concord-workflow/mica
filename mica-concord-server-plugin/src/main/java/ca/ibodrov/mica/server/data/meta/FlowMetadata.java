package ca.ibodrov.mica.server.data.meta;

import java.util.List;

public record FlowMetadata(String name, List<FlowParameter> inParameters, List<FlowParameter> outParameters) {
}
