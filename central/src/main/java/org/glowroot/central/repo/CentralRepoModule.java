/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.central.repo;

import java.util.List;

import com.datastax.driver.core.KeyspaceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.central.util.ClusterManager;
import org.glowroot.central.util.Session;
import org.glowroot.common.config.ImmutableUserConfig;
import org.glowroot.common.util.Clock;
import org.glowroot.ui.PasswordHash;

public class CentralRepoModule {

    private static final Logger startupLogger = LoggerFactory.getLogger("org.glowroot");

    private final AgentRollupDao agentRollupDao;
    private final AgentConfigDao agentConfigDao;
    private final UserDao userDao;
    private final RoleDao roleDao;
    private final ConfigRepositoryImpl configRepository;
    private final TransactionTypeDao transactionTypeDao;
    private final AggregateDao aggregateDao;
    private final TraceAttributeNameDao traceAttributeNameDao;
    private final TraceDao traceDao;
    private final GaugeValueDao gaugeValueDao;
    private final SyntheticResultDao syntheticResultDao;
    private final EnvironmentDao environmentDao;
    private final HeartbeatDao heartbeatDao;
    private final IncidentDao incidentDao;

    public CentralRepoModule(ClusterManager clusterManager, Session session,
            KeyspaceMetadata keyspaceMetadata, String cassandraSymmetricEncryptionKey, Clock clock)
            throws Exception {
        CentralConfigDao centralConfigDao = new CentralConfigDao(session, clusterManager);
        agentRollupDao = new AgentRollupDao(session, clusterManager);
        agentConfigDao = new AgentConfigDao(session, clusterManager);
        userDao = new UserDao(session, keyspaceMetadata, clusterManager);
        roleDao = new RoleDao(session, keyspaceMetadata, clusterManager);
        configRepository = new ConfigRepositoryImpl(agentRollupDao, agentConfigDao,
                centralConfigDao, userDao, roleDao, cassandraSymmetricEncryptionKey);
        transactionTypeDao = new TransactionTypeDao(session, configRepository, clusterManager);
        FullQueryTextDao fullQueryTextDao = new FullQueryTextDao(session, configRepository);
        aggregateDao = new AggregateDao(session, agentRollupDao, transactionTypeDao,
                fullQueryTextDao, configRepository, clock);
        traceAttributeNameDao =
                new TraceAttributeNameDao(session, configRepository, clusterManager);
        traceDao = new TraceDao(session, agentRollupDao, transactionTypeDao, fullQueryTextDao,
                traceAttributeNameDao, configRepository, clock);
        gaugeValueDao =
                new GaugeValueDao(session, agentRollupDao, configRepository, clusterManager, clock);
        syntheticResultDao = new SyntheticResultDao(session, configRepository, clock);
        environmentDao = new EnvironmentDao(session);
        heartbeatDao = new HeartbeatDao(session, agentRollupDao, clock);
        incidentDao = new IncidentDao(session, clock);
    }

    public boolean setupAdminUser(List<String> args) throws Exception {
        String username = args.get(0);
        String password = args.get(1);
        if (roleDao.read("Administrator") == null) {
            startupLogger.error("Administrator role does not exist, exiting");
            return false;
        }
        // not using insertIfNotExists in case this command fails on the next line for some reason
        // (while deleting anonymous user) and the command needs to be re-run
        userDao.insert(ImmutableUserConfig.builder()
                .username(username)
                .passwordHash(PasswordHash.createHash(password))
                .addRoles("Administrator")
                .build());
        userDao.delete("anonymous");
        return true;
    }

    public AgentRollupDao getAgentRollupDao() {
        return agentRollupDao;
    }

    public AgentConfigDao getAgentConfigDao() {
        return agentConfigDao;
    }

    public ConfigRepositoryImpl getConfigRepository() {
        return configRepository;
    }

    public TransactionTypeDao getTransactionTypeDao() {
        return transactionTypeDao;
    }

    public AggregateDao getAggregateDao() {
        return aggregateDao;
    }

    public TraceAttributeNameDao getTraceAttributeNameDao() {
        return traceAttributeNameDao;
    }

    public TraceDao getTraceDao() {
        return traceDao;
    }

    public GaugeValueDao getGaugeValueDao() {
        return gaugeValueDao;
    }

    public SyntheticResultDao getSyntheticResultDao() {
        return syntheticResultDao;
    }

    public EnvironmentDao getEnvironmentDao() {
        return environmentDao;
    }

    public HeartbeatDao getHeartbeatDao() {
        return heartbeatDao;
    }

    public IncidentDao getIncidentDao() {
        return incidentDao;
    }
}
