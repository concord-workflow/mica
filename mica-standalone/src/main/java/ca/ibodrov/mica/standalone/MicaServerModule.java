package ca.ibodrov.mica.standalone;

import ca.ibodrov.mica.server.MicaPluginModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.server.*;
import com.walmartlabs.concord.server.agent.AgentModule;
import com.walmartlabs.concord.server.audit.AuditLogModule;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.cfg.ConfigurationModule;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationModule;
import com.walmartlabs.concord.server.events.EventModule;
import com.walmartlabs.concord.server.metrics.MetricModule;
import com.walmartlabs.concord.server.org.OrganizationModule;
import com.walmartlabs.concord.server.policy.PolicyModule;
import com.walmartlabs.concord.server.process.ProcessModule;
import com.walmartlabs.concord.server.repository.RepositoryModule;
import com.walmartlabs.concord.server.role.RoleModule;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import com.walmartlabs.concord.server.security.SecurityModule;
import com.walmartlabs.concord.server.security.apikey.ApiKeyModule;
import com.walmartlabs.concord.server.task.TaskSchedulerModule;
import com.walmartlabs.concord.server.template.TemplateModule;
import com.walmartlabs.concord.server.user.UserModule;

import java.security.SecureRandom;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

public class MicaServerModule implements Module {

    private final Config config;

    public MicaServerModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(UuidGenerator.class).in(SINGLETON);
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
        binder.bind(ConcordObjectMapper.class).in(SINGLETON);

        binder.install(new ConfigurationModule(config));
        binder.install(new MetricModule());

        binder.install(new DatabaseConfigurationModule());
        binder.install(new DatabaseModule());

        binder.install(new TaskSchedulerModule());
        binder.bind(BackgroundTasks.class).in(SINGLETON);

        binder.bind(Listeners.class).in(SINGLETON);
        binder.bind(SecureRandom.class).toProvider(SecureRandomProvider.class);

        binder.bind(DependencyManagerConfiguration.class).toProvider(DependencyManagerConfigurationProvider.class);

        binder.install(new ApiServerModule());

        binder.install(new ApiKeyModule());
        binder.install(new AuditLogModule());
        binder.install(new MicaPluginModule(config));
        binder.install(new OrganizationModule());
        binder.install(new ProcessModule());
        binder.install(new RepositoryModule());
        binder.install(new RoleModule());
        binder.install(new SecurityModule());
        binder.install(new UserModule());

        bindJaxRsResource(binder, ServerResource.class);

        newSetBinder(binder, ProcessEventListener.class);
    }
}
