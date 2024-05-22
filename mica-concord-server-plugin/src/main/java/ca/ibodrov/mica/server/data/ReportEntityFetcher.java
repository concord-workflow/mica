package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.exceptions.StoreException;
import ca.ibodrov.mica.server.reports.ValidateAllReport;

import javax.inject.Inject;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class ReportEntityFetcher implements EntityFetcher {

    private static final String URI_SCHEME = "mica+report";

    private final ValidateAllReport validateAllReport;

    @Inject
    public ReportEntityFetcher(ValidateAllReport validateAllReport) {
        this.validateAllReport = requireNonNull(validateAllReport);
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        return request.uri().map(uri -> URI_SCHEME.equals(uri.getScheme())).orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException("URI is required"));
        var reportName = uri.getHost();
        switch (reportName) {
            case "validateAll" -> {
                var queryParams = new QueryParams(uri.getQuery());
                var reportUnevaluatedProperties = queryParams.getFirst("reportUnevaluatedProperties")
                        .map(Boolean::parseBoolean)
                        .orElse(false);
                var options = new ValidateAllReport.Options(reportUnevaluatedProperties);
                return () -> Stream.of(validateAllReport.run(options));
            }
            default -> throw new StoreException("Unsupported report: %s".formatted(reportName));
        }
    }
}
