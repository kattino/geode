/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.management.internal.cli;

import static org.apache.geode.distributed.ConfigurationProperties.GROUPS;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_START;
import static org.apache.geode.distributed.ConfigurationProperties.NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.Set;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.execute.FunctionAdapter;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.management.DistributedRegionMXBean;
import org.apache.geode.management.ManagementService;
import org.apache.geode.management.RegionMXBean;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.LogWriterUtils;
import org.apache.geode.test.dunit.SerializableCallable;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.Wait;
import org.apache.geode.test.dunit.WaitCriterion;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.geode.test.junit.categories.DistributedTest;

@Category(DistributedTest.class)
public class CliUtilDUnitTest extends JUnit4CacheTestCase {

  public static final String COMMON_REGION = "region1";
  public static final String COMMON_REGION_GROUP1 = "region_group1";
  public static final String COMMON_REGION_GROUP2 = "region_group2";
  public static final String REGION_MEMBER1_GROUP1 = "region_member1_group1";
  public static final String REGION_MEMBER2_GROUP1 = "region_member2_group1";
  public static final String REGION_MEMBER1_GROUP2 = "region_member1_group2";
  public static final String REGION_MEMBER2_GROUP2 = "region_member2_group2";

  public static final String MEMBER_1_GROUP1 = "member1_group1";
  public static final String MEMBER_2_GROUP1 = "member2_group1";
  public static final String MEMBER_1_GROUP2 = "member1_group2";
  public static final String MEMBER_2_GROUP2 = "member2_group2";

  public static final String GROUP1 = "group1";
  public static final String GROUP2 = "group2";

  private static final long serialVersionUID = 1L;

  @Override
  public final void preTearDownCacheTestCase() throws Exception {
    destroySetup();
  }

  protected final void destroySetup() {
    disconnectAllFromDS();
  }

  @SuppressWarnings("serial")
  void setupMembersWithIdsAndGroups() {
    final VM vm1 = Host.getHost(0).getVM(0);
    final VM vm2 = Host.getHost(0).getVM(1);
    final VM vm3 = Host.getHost(0).getVM(2);
    final VM vm4 = Host.getHost(0).getVM(3);

    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        createCacheWithMemberIdAndGroup(MEMBER_1_GROUP1, GROUP1);
        createRegion(REGION_MEMBER1_GROUP1);
        createRegion(COMMON_REGION_GROUP1);
        createRegion(COMMON_REGION);
      }
    });

    vm2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        createCacheWithMemberIdAndGroup(MEMBER_2_GROUP1, GROUP1);
        createRegion(REGION_MEMBER2_GROUP1);
        createRegion(COMMON_REGION_GROUP1);
        createRegion(COMMON_REGION);
      }
    });

    vm3.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        createCacheWithMemberIdAndGroup(MEMBER_1_GROUP2, GROUP2);
        createRegion(REGION_MEMBER1_GROUP2);
        createRegion(COMMON_REGION_GROUP2);
        createRegion(COMMON_REGION);
      }
    });

    vm4.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        createCacheWithMemberIdAndGroup(MEMBER_2_GROUP2, GROUP2);
        createRegion(REGION_MEMBER2_GROUP2);
        createRegion(COMMON_REGION_GROUP2);
        createRegion(COMMON_REGION);
      }
    });

    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        startManager();
      }
    });
  }

  private void startManager() {
    final ManagementService service = ManagementService.getManagementService(getCache());
    service.startManager();
    assertEquals(true, service.isManager());
    assertNotNull(service.getManagerMXBean());
    assertTrue(service.getManagerMXBean().isRunning());
    final WaitCriterion waitForMaangerMBean = new WaitCriterion() {
      @Override
      public boolean done() {
        boolean flag = checkBean(COMMON_REGION, 4) && checkBean(COMMON_REGION_GROUP1, 2)
            && checkBean(COMMON_REGION_GROUP2, 2) && checkBean(REGION_MEMBER1_GROUP1, 1)
            && checkBean(REGION_MEMBER2_GROUP1, 1) && checkBean(REGION_MEMBER1_GROUP2, 1)
            && checkBean(REGION_MEMBER2_GROUP2, 1);
        if (!flag) {
          LogWriterUtils.getLogWriter().info("Still probing for mbeans");
          return false;
        } else {
          LogWriterUtils.getLogWriter()
              .info("All distributed region mbeans are federated to manager.");
          return true;
        }
      }

      private boolean checkBean(String string, int memberCount) {
        DistributedRegionMXBean bean2 =
            service.getDistributedRegionMXBean(Region.SEPARATOR + string);
        LogWriterUtils.getLogWriter()
            .info("DistributedRegionMXBean for region=" + string + " is " + bean2);
        if (bean2 == null)
          return false;
        else {
          int members = bean2.getMemberCount();
          LogWriterUtils.getLogWriter().info("DistributedRegionMXBean for region=" + string
              + " is aggregated for " + memberCount + " expected count=" + memberCount);
          if (members < memberCount) {
            return false;
          } else {
            return true;
          }
        }
      }

      @Override
      public String description() {
        return "Probing for ManagerMBean";
      }
    };

    Wait.waitForCriterion(waitForMaangerMBean, 120000, 2000, true);
    LogWriterUtils.getLogWriter().info("Manager federation is complete");
  }

  @SuppressWarnings("rawtypes")
  private Region createRegion(String regionName) {
    RegionFactory regionFactory = getCache().createRegionFactory(RegionShortcut.REPLICATE);
    Region region = regionFactory.create(regionName);
    final ManagementService service = ManagementService.getManagementService(getCache());
    assertNotNull(service.getMemberMXBean());
    RegionMXBean bean = service.getLocalRegionMBean(Region.SEPARATOR + regionName);
    assertNotNull(bean);
    LogWriterUtils.getLogWriter().info("Created region=" + regionName + " Bean=" + bean);
    return region;
  }

  public void createCacheWithMemberIdAndGroup(String memberName, String groupName) {
    Properties localProps = new Properties();
    localProps.setProperty(NAME, memberName);
    localProps.setProperty(GROUPS, groupName);
    localProps.setProperty(JMX_MANAGER, "true");
    localProps.setProperty(JMX_MANAGER_START, "false");
    int jmxPort = AvailablePortHelper.getRandomAvailableTCPPort();
    localProps.setProperty(JMX_MANAGER_PORT, "" + jmxPort);
    LogWriterUtils.getLogWriter().info("Set jmx-port=" + jmxPort);
    getSystem(localProps);
    getCache();
    final ManagementService service = ManagementService.getManagementService(getCache());
    assertNotNull(service.getMemberMXBean());
  }

  @SuppressWarnings("serial")
  @Test
  public void testCliUtilMethods() {
    setupMembersWithIdsAndGroups();

    final VM vm1 = Host.getHost(0).getVM(0);

    LogWriterUtils.getLogWriter().info("testFor - findMembersOrThrow");
    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        verifyFindAllMatchingMembers();
      }
    });

    final String id = (String) vm1.invoke(new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        InternalCache cache = getCache();
        return cache.getDistributedSystem().getDistributedMember().getId();
      }
    });

    LogWriterUtils.getLogWriter().info("testFor - getDistributedMemberByNameOrId");
    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        getDistributedMemberByNameOrId(MEMBER_1_GROUP1, id);
      }
    });

    LogWriterUtils.getLogWriter().info("testFor - executeFunction");
    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        verifyExecuteFunction();
      }
    });

    LogWriterUtils.getLogWriter().info("testFor - getRegionAssociatedMembers");
    vm1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        getRegionAssociatedMembers();
      }
    });
  }

  public void verifyFindAllMatchingMembers() {
    Set<DistributedMember> set = CliUtil.findMembers(GROUP1.split(","), null);
    assertNotNull(set);
    assertEquals(2, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP1));

    set = CliUtil.findMembers(new String[] {"group1", "group2"}, null);
    assertNotNull(set);
    assertEquals(4, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_1_GROUP2));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP2));

    set = CliUtil.findMembers(null, MEMBER_1_GROUP1.split(","));
    assertNotNull(set);
    assertEquals(1, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));

    set = CliUtil.findMembers(null, new String[] {"member1_group1", "member2_group2"});
    assertNotNull(set);
    assertEquals(2, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP2));
  }

  private Object containsMember(Set<DistributedMember> set, String string) {
    boolean returnValue = false;
    for (DistributedMember member : set)
      if (member.getName().equals(string))
        return true;
    return returnValue;
  }

  public void getDistributedMemberByNameOrId(String name, String id) {
    DistributedMember member = CliUtil.getDistributedMemberByNameOrId(name);
    assertNotNull(member);

    member = CliUtil.getDistributedMemberByNameOrId(id);
    assertNotNull(member);
  }

  public void verifyExecuteFunction() {
    DunitFunction function = new DunitFunction("myfunction");
    Set<DistributedMember> set;
    @SuppressWarnings("rawtypes")
    Region region1 = getCache().getRegion(COMMON_REGION);
    region1.clear();
    set = CliUtil.findMembers(GROUP1.split(","), null);
    assertEquals(2, set.size());
    ResultCollector collector = CliUtil.executeFunction(function, "executeOnGroup", set);
    collector.getResult();
    assertEquals(2, region1.size());
    assertTrue(region1.containsKey(MEMBER_1_GROUP1));
    assertTrue(region1.containsKey(MEMBER_2_GROUP1));
    assertEquals("executeOnGroup", region1.get(MEMBER_1_GROUP1));
    assertEquals("executeOnGroup", region1.get(MEMBER_2_GROUP1));
  }

  public void getRegionAssociatedMembers() {
    String region_group1 = "/region_group1";
    String region1 = "/region1";
    String region_member2_group1 = "/region_member2_group1";

    InternalCache cache = getCache();

    Set<DistributedMember> set = CliUtil.getRegionAssociatedMembers(region1, cache, true);
    assertNotNull(set);
    assertEquals(4, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_1_GROUP2));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP2));

    /*
     * TODO: This is failing because last param is not considered in method set =
     * CliUtil.getRegionAssociatedMembers(region1, cache, false); assertNotNull(set);
     * assertIndexDetailsEquals(1, set.size());
     */

    set = CliUtil.getRegionAssociatedMembers(region_group1, cache, true);
    assertNotNull(set);
    assertEquals(2, set.size());
    assertEquals(true, containsMember(set, MEMBER_1_GROUP1));
    assertEquals(true, containsMember(set, MEMBER_2_GROUP1));

    set = CliUtil.getRegionAssociatedMembers(region_member2_group1, cache, true);
    assertNotNull(set);
    assertEquals(1, set.size());
    assertEquals(true, containsMember(set, MEMBER_2_GROUP1));
  }

  public static class DunitFunction extends FunctionAdapter {

    private static final long serialVersionUID = 1L;
    private String id;

    public DunitFunction(String fid) {
      this.id = fid;
    }

    @Override
    public void execute(FunctionContext context) {
      Object object = context.getArguments();
      InternalCache cache = (InternalCache) CacheFactory.getAnyInstance();
      @SuppressWarnings("rawtypes")
      Region region = cache.getRegion(COMMON_REGION);
      String id = cache.getDistributedSystem().getDistributedMember().getName();
      region.put(id, object);
      LogWriterUtils.getLogWriter().info("Completed executeFunction on member : " + id);
      context.getResultSender().lastResult(true);
    }

    @Override
    public String getId() {
      return id;
    }
  }

}
