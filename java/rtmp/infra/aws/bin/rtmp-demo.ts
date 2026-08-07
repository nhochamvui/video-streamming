#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { CheapEcsEc2Stack } from '../lib/cheap-ecs-ec2-stack';
import { getConfig } from '../lib/config';
import { ManagedEcsFargateStack } from '../lib/managed-ecs-fargate-stack';

const app = new cdk.App();
const config = getConfig(app);

if (config.deployMode === 'managed') {
  new ManagedEcsFargateStack(app, 'RtmpManagedDemoStack', { config });
} else {
  new CheapEcsEc2Stack(app, 'RtmpCheapDemoStack', { config });
}
