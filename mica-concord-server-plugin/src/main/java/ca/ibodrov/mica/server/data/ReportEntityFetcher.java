package ca.ibodrov.mica.server.data;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

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
