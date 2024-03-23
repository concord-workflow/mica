package ca.ibodrov.mica.server.data.meta;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.meta.ParseResult.items;
import static ca.ibodrov.mica.server.data.meta.ParseResult.warnings;

public class MetadataScanner {

    public Metadata scan(Path rootPath) {
        try (var paths = Files.walk(rootPath)) {
            var files = paths.filter(this::isConcordYamlFile)
                    .map(p -> {
                        var relativePath = rootPath.relativize(p).toString();
                        var flows = parseConcordYamlFile(p);
//                        return new ConcordFileMetadata(relativePath, flows);
                        return null;
                    }).toList();
//            return new Metadata(files);
            return null;
        } catch (IOException e) {
            throw new MetadataScannerException("Error scanning " + rootPath + ": " + e.getMessage(), e);
        }
    }

    private boolean isConcordYamlFile(Path p) {
        var n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".concord.yml") || n.endsWith(".concord.yaml");
    }

    static private ParseResult<FlowMetadata, ScannerWarning> parseConcordYamlFile(Path path) {
        try (var lines = Files.lines(path)) {
            return parseConcordYaml(lines);
        } catch (IOException e) {
            throw new MetadataScannerException("Error parsing " + path + ": " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    static ParseResult<FlowMetadata, ScannerWarning> parseConcordYaml(Stream<String> lines) {
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

        var flows = new ArrayList<FlowMetadata>();
        var flowName = (String) null;
        var inParameters = new ArrayList<FlowParameter>();
        var outParameters = new ArrayList<FlowParameter>();
        var warnings = new ArrayList<ScannerWarning>();
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
                    warnings.add(new ScannerWarning("Invalid indent", lineNum, line));
                    break;
                }
            }

            if (line.length() < minIndent) {
                continue;
            }

            line = line.substring(minIndent);

            switch (state) {
                case START -> {
                    if (line.equals("flows:")) {
                        state = State.FLOWS_SECTION;
                    }
                }
                case FLOWS_SECTION -> {
                    var l = line.stripLeading();
                    if (l.startsWith("##")) {
                        state = State.PARAMS_START;
                        sectionIndent = line.length() - l.length();
                    }
                }
                case PARAMS_START -> {
                    if (line.length() < sectionIndent) {
                        state = State.PARAMS_END;
                        warnings.add(new ScannerWarning("Invalid indent", lineNum, line));
                        break;
                    }

                    var l = line.substring(sectionIndent);

                    if (l.startsWith("#  in:")) {
                        state = State.IN_PARAMS;
                    } else if (l.startsWith("#  out:")) {
                        state = State.OUT_PARAMS;
                    } else if (l.startsWith("##")) {
                        state = State.PARAMS_END;
                    }
                }
                case IN_PARAMS -> {
                    var l = line.substring(sectionIndent);
                    if (l.startsWith("##")) {
                        state = State.PARAMS_END;
                        break;
                    } else if (l.startsWith("#  out:")) {
                        state = State.OUT_PARAMS;
                        break;
                    }

                    var result = parseParameter(line, lineNum);
                    warnings.addAll(result.warnings());
                    inParameters.addAll(result.items());
                }
                case OUT_PARAMS -> {
                    var l = line.substring(sectionIndent);
                    if (l.startsWith("##")) {
                        state = State.PARAMS_END;
                        break;
                    }

                    var result = parseParameter(line, lineNum);
                    warnings.addAll(result.warnings());
                    outParameters.addAll(result.items());
                }
                case PARAMS_END -> {
                    if (line.isBlank() || !line.endsWith(":") || line.length() < sectionIndent) {
                        warnings.add(new ScannerWarning("Expected a flow name", lineNum, line));
                        state = State.FLOWS_SECTION;
                        break;
                    }

                    var n = line.substring(sectionIndent).stripTrailing();
                    if (!n.endsWith(":")) {
                        warnings.add(new ScannerWarning("Expected a flow name", lineNum, line));
                        state = State.FLOWS_SECTION;
                        break;
                    }

                    flowName = n.substring(0, n.length() - 1);
                    state = State.FLOW;
                }
                case FLOW -> {
                    flows.add(new FlowMetadata(flowName, inParameters, outParameters));

                    flowName = null;
                    inParameters = new ArrayList<>();
                    outParameters = new ArrayList<>();
                    warnings = new ArrayList<>();
                    state = State.FLOWS_SECTION;
                }
            }
        }

        return new ParseResult<>(flows, warnings);
    }

    private static ParseResult<FlowParameter, ScannerWarning> parseParameter(String line, int lineNum) {
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
