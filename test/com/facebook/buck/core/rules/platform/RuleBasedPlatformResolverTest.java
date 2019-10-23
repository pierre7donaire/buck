/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.core.rules.platform;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.platform.impl.ConstraintBasedPlatform;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RuleBasedPlatformResolverTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void requestingPlatformForWrongTypeThrowsException() {

    BuildTarget constraint =
        ConfigurationBuildTargetFactoryForTests.newInstance("//constraint:setting");
    RuleBasedPlatformResolver resolver =
        new RuleBasedPlatformResolver(
            new ConfigurationRuleResolver() {
              @Override
              public <R extends ConfigurationRule> R getRule(
                  BuildTarget buildTarget, Class<R> ruleClass, DependencyStack dependencyStack) {
                return ruleClass.cast(new ConstraintSettingRule(constraint));
              }
            },
            new ThrowingConstraintResolver());

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "//constraint:setting is used as a target platform, but not declared using `platform` rule");

    resolver.getPlatform(constraint, DependencyStack.root());
  }

  @Test
  public void requestingPlatformForPlatformRuleCreatesPlatform() {

    BuildTarget platformTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//platform:platform");
    BuildTarget constraintValue =
        ConfigurationBuildTargetFactoryForTests.newInstance("//constraint:value");
    BuildTarget constraintSetting =
        ConfigurationBuildTargetFactoryForTests.newInstance("//constraint:setting");
    ConstraintSettingRule constraintSettingRule = new ConstraintSettingRule(constraintSetting);

    ConfigurationRuleResolver configurationRuleResolver =
        new ConfigurationRuleResolver() {
          @Override
          public <R extends ConfigurationRule> R getRule(
              BuildTarget buildTarget, Class<R> ruleClass, DependencyStack dependencyStack) {
            if (buildTarget.equals(platformTarget)) {
              return ruleClass.cast(
                  PlatformRule.of(
                      platformTarget,
                      "platform",
                      ImmutableSortedSet.of(constraintValue),
                      ImmutableSortedSet.of()));
            }
            if (buildTarget.equals(constraintValue)) {
              return ruleClass.cast(
                  new ConstraintValueRule(constraintValue, constraintSettingRule));
            }
            if (buildTarget.equals(constraintSetting)) {
              return ruleClass.cast(constraintSettingRule);
            }
            throw new IllegalArgumentException("Invalid build target: " + buildTarget);
          }
        };

    RuleBasedPlatformResolver resolver =
        new RuleBasedPlatformResolver(
            configurationRuleResolver, new RuleBasedConstraintResolver(configurationRuleResolver));

    ConstraintBasedPlatform platform =
        (ConstraintBasedPlatform) resolver.getPlatform(platformTarget, DependencyStack.root());

    assertEquals("//platform:platform", platform.toString());
    assertEquals(1, platform.getConstraintValues().size());
    assertEquals(
        constraintValue, Iterables.getOnlyElement(platform.getConstraintValues()).getBuildTarget());
  }
}
