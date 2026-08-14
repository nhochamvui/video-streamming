#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { CheapEcsEc2AppStack, CheapEcsEc2InfraStack } from '../lib/cheap-ecs-ec2-stack';
import { getConfig } from '../lib/config';
import { ManagedEcsFargateAppStack, ManagedEcsFargateInfraStack } from '../lib/managed-ecs-fargate-stack';

const app = new cdk.App();
const config = getConfig(app);

if (config.deployMode === 'managed') {
  const infra = new ManagedEcsFargateInfraStack(app, 'RtmpManagedDemoInfraStack', { config });
  const appStack = new ManagedEcsFargateAppStack(app, 'RtmpManagedDemoAppStack', { config });
  appStack.addDependency(infra);
} else {
  const infra = new CheapEcsEc2InfraStack(app, 'RtmpCheapDemoInfraStack', { config });
  const appStack = new CheapEcsEc2AppStack(app, 'RtmpCheapDemoAppStack', { config });
  appStack.addDependency(infra);
}
