package ca.ibodrov.mica.server;

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

import ca.ibodrov.mica.db.MicaDatabaseModule;
import ca.ibodrov.mica.server.api.*;
import ca.ibodrov.mica.server.data.*;
import ca.ibodrov.mica.server.data.git.ConcordGitEntityFetcher;
import ca.ibodrov.mica.server.data.js.GraalJsEvaluator;
import ca.ibodrov.mica.server.data.js.JsEvaluator;
import ca.ibodrov.mica.server.data.jsonStore.JsonStoreEntityFetcher;
import ca.ibodrov.mica.server.data.remote.RemoteMicaEntityFetcher;
import ca.ibodrov.mica.server.data.s3.ConcordSecretS3CredentialsProvider;
import ca.ibodrov.mica.server.data.s3.S3ClientManager;
import ca.ibodrov.mica.server.data.s3.S3CredentialsProvider;
import ca.ibodrov.mica.server.data.s3.S3EntityFetcher;
import ca.ibodrov.mica.server.data.viewRenderHistory.ViewRenderHistoryCleaner;
import ca.ibodrov.mica.server.data.viewRenderHistory.ViewRenderHistoryEntityFetcher;
import ca.ibodrov.mica.server.exceptions.DataAccessExceptionMapper;
import ca.ibodrov.mica.server.exceptions.StoreExceptionExceptionMapper;
import ca.ibodrov.mica.server.exceptions.ViewProcessorExceptionMapper;
import ca.ibodrov.mica.server.reports.Report;
import ca.ibodrov.mica.server.reports.ValidateAllReport;
import ca.ibodrov.mica.server.ui.*;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.config.ConfigModule;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.ext.ExceptionMapper;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;
import static com.walmartlabs.concord.server.Utils.bindServletFilter;

/**
 * Mica's Guice module. Intended to use as a concord-server plugin. For the
 * standalone version see MicaServerModule in mica-standalone.
 * <p/>
 * Several things are assumed to be provided by the environment:
 * <ul>
 * <li>{@link com.fasterxml.jackson.databind.ObjectMapper}</li>
 * <li>{@link com.walmartlabs.concord.db.DatabaseConfiguration} annotated as
 * {@link com.walmartlabs.concord.db.MainDB}</li>
 * <li>{@link com.codahale.metrics.MetricRegistry}</li>
 * </ul>
 */
@Named
public class MicaPluginModule implements Module {

    @Override
    public void configure(Binder binder) {
        // database

        binder.install(new MicaDatabaseModule());

        // authentication handlers

        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(MicaAuthenticationHandler.class);

        // authorization

        binder.bind(ApiSecurityFilter.class).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(ApiSecurityFilter.class);

        // servlets

        newSetBinder(binder, HttpServlet.class).addBinding().to(SwaggerServlet.class).in(SINGLETON);

        // filter chains

        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(MicaFilterChainConfigurator.class);

        // custom @Context support

        bindServletFilter(binder, UserPrincipalContextProvider.class);

        // exception mappers

        bindExceptionMapper(binder, DataAccessExceptionMapper.class);
        bindExceptionMapper(binder, StoreExceptionExceptionMapper.class);
        bindExceptionMapper(binder, ViewProcessorExceptionMapper.class);

        // jax-rs resources

        bindJaxRsResource(binder, BatchOperationResource.class);
        bindJaxRsResource(binder, DashboardResource.class);
        bindJaxRsResource(binder, DownloadFolderResource.class);
        bindJaxRsResource(binder, EditorSchemaResource.class);
        bindJaxRsResource(binder, EntityHistoryResource.class);
        bindJaxRsResource(binder, EntityListResource.class);
        bindJaxRsResource(binder, EntityResource.class);
        bindJaxRsResource(binder, EntityUploadResource.class);
        bindJaxRsResource(binder, ExportResource.class);
        bindJaxRsResource(binder, ImportResource.class);
        bindJaxRsResource(binder, ReportResource.class);
        bindJaxRsResource(binder, SystemResource.class);
        bindJaxRsResource(binder, ViewResource.class);
        bindJaxRsResource(binder, WhoamiResource.class);

        // reports

        newSetBinder(binder, Report.class).addBinding().to(ValidateAllReport.class);

        // entity fetchers

        newSetBinder(binder, EntityFetcher.class).addBinding().to(ConcordGitEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(InternalEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(JsonStoreEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(RemoteMicaEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(ReportEntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(S3EntityFetcher.class);
        newSetBinder(binder, EntityFetcher.class).addBinding().to(ViewRenderHistoryEntityFetcher.class);
        binder.bind(EntityFetchers.class).in(SINGLETON);

        // tasks

        newSetBinder(binder, ScheduledTask.class).addBinding().to(EntityHistoryCleaner.class);
        newSetBinder(binder, ScheduledTask.class).addBinding().to(ViewRenderHistoryCleaner.class);

        // other beans

        binder.bind(BuiltinSchemas.class).in(SINGLETON);
        binder.bind(EntityKindStore.class).in(SINGLETON);
        binder.bind(EntityStore.class).in(SINGLETON);
        binder.bind(JsEvaluator.class).to(GraalJsEvaluator.class);
        binder.bind(JsonPathEvaluator.class).in(SINGLETON);
        binder.bind(S3ClientManager.class).in(SINGLETON);
        binder.bind(S3CredentialsProvider.class).to(ConcordSecretS3CredentialsProvider.class);
        binder.bind(UuidGenerator.class).in(SINGLETON);
        binder.bind(ViewCache.class).toInstance(ViewCache.inMemory());

        binder.bind(InitialDataLoader.class).asEagerSingleton();
    }

    public static <K extends ExceptionMapper<?> & Component> void bindExceptionMapper(Binder binder, Class<K> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(klass);
        newSetBinder(binder, ExceptionMapper.class).addBinding().to(klass);
    }
}
