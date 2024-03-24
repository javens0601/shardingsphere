/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.encrypt.distsql.handler.provider;

import org.apache.shardingsphere.distsql.handler.engine.update.ral.rule.spi.database.ImportRuleConfigurationProvider;
import org.apache.shardingsphere.distsql.handler.exception.algorithm.MissingRequiredAlgorithmException;
import org.apache.shardingsphere.infra.exception.rule.DuplicateRuleException;
import org.apache.shardingsphere.encrypt.api.config.EncryptRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.config.rule.EncryptColumnItemRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.config.rule.EncryptColumnRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.config.rule.EncryptTableRuleConfiguration;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Encrypt import rule configuration provider.
 */
public final class EncryptImportRuleConfigurationProvider implements ImportRuleConfigurationProvider<EncryptRuleConfiguration> {
    
    @Override
    public void check(final String databaseName, final EncryptRuleConfiguration ruleConfig) {
        checkTables(databaseName, ruleConfig);
        checkEncryptors(ruleConfig);
        checkTableEncryptorsExisted(databaseName, ruleConfig);
    }
    
    private void checkTables(final String databaseName, final EncryptRuleConfiguration ruleConfig) {
        Collection<String> tableNames = ruleConfig.getTables().stream().map(EncryptTableRuleConfiguration::getName).collect(Collectors.toList());
        Collection<String> duplicatedTables = tableNames.stream().collect(Collectors.groupingBy(each -> each, Collectors.counting())).entrySet().stream()
                .filter(each -> each.getValue() > 1).map(Entry::getKey).collect(Collectors.toSet());
        ShardingSpherePreconditions.checkState(duplicatedTables.isEmpty(), () -> new DuplicateRuleException("ENCRYPT", databaseName, duplicatedTables));
    }
    
    private void checkEncryptors(final EncryptRuleConfiguration ruleConfig) {
        ruleConfig.getEncryptors().values().forEach(each -> TypedSPILoader.checkService(EncryptAlgorithm.class, each.getType(), each.getProps()));
    }
    
    private void checkTableEncryptorsExisted(final String databaseName, final EncryptRuleConfiguration ruleConfig) {
        Collection<EncryptColumnRuleConfiguration> columns = new LinkedList<>();
        ruleConfig.getTables().forEach(each -> columns.addAll(each.getColumns()));
        Collection<String> notExistedEncryptors = columns.stream().map(optional -> optional.getCipher().getEncryptorName()).collect(Collectors.toList());
        notExistedEncryptors.addAll(
                columns.stream().map(optional -> optional.getLikeQuery().map(EncryptColumnItemRuleConfiguration::getEncryptorName).orElse(null)).filter(Objects::nonNull).collect(Collectors.toList()));
        notExistedEncryptors.addAll(columns.stream().map(optional -> optional.getAssistedQuery().map(EncryptColumnItemRuleConfiguration::getEncryptorName).orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toList()));
        Collection<String> encryptors = ruleConfig.getEncryptors().keySet();
        notExistedEncryptors.removeIf(encryptors::contains);
        ShardingSpherePreconditions.checkState(notExistedEncryptors.isEmpty(), () -> new MissingRequiredAlgorithmException(databaseName, notExistedEncryptors));
    }
    
    @Override
    public Class<EncryptRuleConfiguration> getType() {
        return EncryptRuleConfiguration.class;
    }
}
