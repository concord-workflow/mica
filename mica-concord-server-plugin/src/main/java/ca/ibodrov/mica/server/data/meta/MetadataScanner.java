package ca.ibodrov.mica.server.data.meta;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.meta.OperationResult.items;
import static ca.ibodrov.mica.server.data.meta.OperationResult.warnings;

public class MetadataScanner {

    public Metadata scan(Path rootPath) {
        try (var paths = Files.walk(rootPath)) {
            var files = paths.filter(this::isConcordYamlFile)
                    .map(p -> {
                        var relativePath = rootPath.relativize(p).toString();
                        var result = parseConcordYamlFile(p);
                        return new ConcordFileMetadata(relativePath, result.items(), result.warnings());
                    }).toList();
            return new Metadata(files);
        } catch (IOException e) {
            throw new MetadataScannerException("Error scanning " + rootPath + ": " + e.getMessage(), e);
        }
    }

    private boolean isConcordYamlFile(Path p) {
        var n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".concord.yml") || n.endsWith(".concord.yaml");
    }

    static private OperationResult<FlowMetadata, ScannerWarning> parseConcordYamlFile(Path path) {
        try (var lines = Files.lines(path)) {
            return parseConcordYaml(lines);
        } catch (IOException e) {
            throw new MetadataScannerException("Error parsing " + path + ": " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    static OperationResult<FlowMetadata, ScannerWarning> parseConcordYaml(Stream<String> lines) {
        // we are looking for specially-formatted comments here
        // therefore we have to parse the file manually

        enum State {
            /** initial state **/
            START,
            /** top-level "flows:" section found **/
            FLOWS_SECTION,
            /** opening "##" sequence found **/
            PARAMS_START,
            /** "in" parameters block **/
            IN_PARAMS,
            /** "out" parameters block **/
            OUT_PARAMS,
            /** closing "##" sequence found **/
            PARAMS_END,
            /** flow marker found **/
            FLOW,
        }

        class PartialFlowMetadata {
            Optional<String> flowName = Optional.empty();
            List<FlowParameter> inParameters = new ArrayList<>();
            List<FlowParameter> outParameters = new ArrayList<>();
        }

        var partialFlows = new ArrayList<PartialFlowMetadata>();
        var flow = new PartialFlowMetadata();
        var partialWarnings = new ArrayList<ScannerWarning>();
        var state = State.START;
        var lineNum = -1;
        var minIndent = (Integer) null;
        var sectionIndent = (Integer) null;

        for (var line : lines.toList()) {
            lineNum++;

            if (line.isBlank()) {
                continue;
            }

            var strippedLeadingPartLine = line.stripLeading();
            if (minIndent == null) {
                minIndent = line.length() - strippedLeadingPartLine.length();
            } else {
                // sanity check
                if (minIndent > line.length() - strippedLeadingPartLine.length()) {
                    partialWarnings.add(new ScannerWarning("Invalid indent", lineNum, line));
                    break;
                }
            }

            if (line.length() < minIndent) {
                continue;
            }

            line = line.substring(minIndent);

            state = switch (state) {
                case START -> {
                    if (line.equals("flows:")) {
                        yield State.FLOWS_SECTION;
                    }
                    yield state;
                }
                case FLOWS_SECTION -> {
                    var l = line.stripLeading();
                    if (l.startsWith("##")) {
                        sectionIndent = line.length() - l.length();
                        yield State.PARAMS_START;
                    }
                    if (l.stripTrailing().endsWith(":")) {
                        flow.flowName = Optional.of(l.substring(0, l.length() - 1));
                        yield State.FLOW;
                    }
                    yield state;
                }
                case PARAMS_START -> {
                    if (line.length() < sectionIndent) {
                        partialWarnings.add(new ScannerWarning("Invalid indent", lineNum, line));
                        yield State.PARAMS_END;
                    }

                    var l = line.substring(sectionIndent);

                    if (l.startsWith("#  in:")) {
                        yield State.IN_PARAMS;
                    } else if (l.startsWith("#  out:")) {
                        yield State.OUT_PARAMS;
                    } else if (l.startsWith("##")) {
                        yield State.PARAMS_END;
                    }

                    yield state;
                }
                case IN_PARAMS -> {
                    var l = line.substring(sectionIndent);
                    if (l.startsWith("##")) {
                        yield State.PARAMS_END;
                    } else if (l.startsWith("#  out:")) {
                        yield State.OUT_PARAMS;
                    }

                    var result = parseParameter(line, lineNum);
                    partialWarnings.addAll(result.warnings());
                    flow.inParameters.addAll(result.items());

                    yield state;
                }
                case OUT_PARAMS -> {
                    var l = line.substring(sectionIndent);
                    if (l.startsWith("##")) {
                        yield State.PARAMS_END;
                    }

                    var result = parseParameter(line, lineNum);
                    partialWarnings.addAll(result.warnings());
                    flow.outParameters.addAll(result.items());

                    yield state;
                }
                case PARAMS_END -> {
                    // we expect a flow marker here

                    if (line.isBlank() || !line.endsWith(":") || line.length() < sectionIndent) {
                        partialWarnings.add(new ScannerWarning("Expected a flow name", lineNum, line));
                        yield State.FLOWS_SECTION;
                    }

                    var n = line.substring(sectionIndent).stripTrailing();
                    if (!n.endsWith(":")) {
                        partialWarnings.add(new ScannerWarning("Expected a flow name", lineNum, line));
                        yield State.FLOWS_SECTION;
                    }

                    flow.flowName = Optional.of(n.substring(0, n.length() - 1));
                    yield State.FLOW;
                }
                case FLOW -> {
                    // TODO validate optional fields
                    partialFlows.add(flow);

                    flow = new PartialFlowMetadata();
                    partialWarnings = new ArrayList<>();
                    yield State.FLOWS_SECTION;
                }
            };
        }

        var flows = partialFlows.stream()
                .map(p -> new FlowMetadata(p.flowName.orElseThrow(),
                        List.copyOf(p.inParameters),
                        List.copyOf(p.outParameters)))
                .toList();

        var warnings = List.copyOf(partialWarnings);
        if (flows.isEmpty()) {
            warnings = ImmutableList.<ScannerWarning>builder()
                    .addAll(warnings)
                    .add(new ScannerWarning("No flows found", 0, null))
                    .build();
        }

        return new OperationResult<>(flows, warnings);
    }

    private static OperationResult<FlowParameter, ScannerWarning> parseParameter(String line, int lineNum) {
        var i = line.indexOf("# ");
        if (i < 0 || i + 1 >= line.length()) {
            return warnings(new ScannerWarning("Expected a parameter", lineNum, line));
        }

        var descriptor = line.substring(i + 1).strip();
        var parts = descriptor.split(": ");
        if (parts.length != 2) {
            return warnings(new ScannerWarning("Invalid parameter descriptor", lineNum, line));
        }

        var parameterName = parts[0];
        var parameterDescriptor = parts[1].strip().split(", ");
        if (parameterDescriptor.length != 3) {
            return warnings(new ScannerWarning("Invalid parameter descriptor", lineNum, line));
        }

        var parameterType = parameterDescriptor[0];
        var parameterMandatory = parameterDescriptor[1].equals("mandatory");
        var parameterDescription = parameterDescriptor[2];

        return items(new FlowParameter(parameterName, parameterType, parameterMandatory, parameterDescription));
    }
}
