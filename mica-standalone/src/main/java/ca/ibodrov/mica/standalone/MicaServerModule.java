package ca.ibodrov.mica.standalone;

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
import ca.ibodrov.mica.server.MicaPluginModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.server.*;
import com.walmartlabs.concord.server.audit.AuditLogModule;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.cfg.ConfigurationModule;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationModule;
import com.walmartlabs.concord.server.metrics.MetricModule;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.OrganizationResource;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreModule;
import com.walmartlabs.concord.server.org.project.ProjectModule;
import com.walmartlabs.concord.server.org.secret.SecretModule;
import com.walmartlabs.concord.server.org.team.TeamModule;
import com.walmartlabs.concord.server.process.ImportManagerProvider;
import com.walmartlabs.concord.server.process.queue.ProcessStatusListener;
import com.walmartlabs.concord.server.repository.RepositoryModule;
import com.walmartlabs.concord.server.role.RoleModule;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;
import com.walmartlabs.concord.server.security.BasicAuthenticationHandler;
import com.walmartlabs.concord.server.security.UnauthenticatedExceptionMapper;
import com.walmartlabs.concord.server.security.UnauthorizedExceptionMapper;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyAuthenticationHandler;
import com.walmartlabs.concord.server.security.apikey.ApiKeyModule;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.security.internal.LocalUserInfoProvider;
import com.walmartlabs.concord.server.task.TaskSchedulerModule;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserModule;
import org.apache.shiro.realm.Realm;

import javax.servlet.http.HttpServlet;
import java.security.SecureRandom;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindExceptionMapper;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

public class MicaServerModule implements Module {

    private final Config config;

    public MicaServerModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, DatabaseChangeLogProvider.class).addBinding().to(MainDBChangeLogProvider.class);

        binder.bind(BackgroundTasks.class).in(SINGLETON);
        binder.bind(ConcordObjectMapper.class).in(SINGLETON);
        binder.bind(DependencyManagerConfiguration.class).toProvider(DependencyManagerConfigurationProvider.class);
        binder.bind(ImportManager.class).toProvider(ImportManagerProvider.class);
        binder.bind(Listeners.class).in(SINGLETON);
        binder.bind(LocalUserInfoProvider.class).in(SINGLETON);
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
        binder.bind(OrganizationDao.class).in(SINGLETON);
        binder.bind(OrganizationManager.class).in(SINGLETON);
        binder.bind(SecureRandom.class).toProvider(SecureRandomProvider.class);
        binder.bind(UserSecurityContext.class);
        binder.bind(UuidGenerator.class).in(SINGLETON);

        newSetBinder(binder, ProjectLoader.class).addBinding().to(DummyProjectLoader.class);

        binder.install(new ConfigurationModule(config));
        binder.install(new DatabaseConfigurationModule());
        binder.install(new MicaStandaloneDatabaseModule());
        binder.install(new MicaPluginModule(config));

        binder.install(new ApiKeyModule());
        binder.install(new ApiServerModule());
        binder.install(new AuditLogModule());
        binder.install(new JsonStoreModule());
        binder.install(new MetricModule());
        binder.install(new ProjectModule());
        binder.install(new RepositoryModule());
        binder.install(new RoleModule());
        binder.install(new SecretModule());
        binder.install(new TaskSchedulerModule());
        binder.install(new TeamModule());
        binder.install(new UserModule());

        bindJaxRsResource(binder, OrganizationResource.class);
        bindJaxRsResource(binder, ServerResource.class);

        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(ApiKeyAuthenticationHandler.class)
                .in(SINGLETON);
        newSetBinder(binder, AuthenticationHandler.class).addBinding().to(BasicAuthenticationHandler.class)
                .in(SINGLETON);
        newSetBinder(binder, ProcessEventListener.class);
        newSetBinder(binder, ProcessLogListener.class);
        newSetBinder(binder, ProcessStatusListener.class);
        newSetBinder(binder, Realm.class).addBinding().to(ApiKeyRealm.class);
        newSetBinder(binder, Realm.class).addBinding().to(InternalRealm.class);
        newSetBinder(binder, UserInfoProvider.class).addBinding().to(LocalUserInfoProvider.class);

        bindExceptionMapper(binder, UnauthorizedExceptionMapper.class);
        bindExceptionMapper(binder, UnauthenticatedExceptionMapper.class);

        newSetBinder(binder, HttpServlet.class).addBinding().to(RedirectToMicaServlet.class).in(SINGLETON);
    }
}
