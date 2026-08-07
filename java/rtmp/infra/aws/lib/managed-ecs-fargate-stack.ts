import * as cdk from 'aws-cdk-lib';
import { Stack } from 'aws-cdk-lib';
import { Peer, Port, SecurityGroup, SubnetType, Vpc } from 'aws-cdk-lib/aws-ec2';
import { CfnCacheCluster, CfnSubnetGroup } from 'aws-cdk-lib/aws-elasticache';
import { Cluster, ContainerImage, FargateService, FargateTaskDefinition, AwsLogDriver, Secret } from 'aws-cdk-lib/aws-ecs';
import { ApplicationLoadBalancer, NetworkLoadBalancer } from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { ManagedPolicy, PolicyStatement, Role, ServicePrincipal } from 'aws-cdk-lib/aws-iam';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { StringParameter } from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import { InfraConfig } from './config';
import { createHlsStorage } from './storage-stack';

export interface ManagedEcsFargateStackProps extends cdk.StackProps {
  readonly config: InfraConfig;
}

export class ManagedEcsFargateStack extends Stack {
  constructor(scope: Construct, id: string, props: ManagedEcsFargateStackProps) {
    super(scope, id, props);

    const { config } = props;
    const storage = createHlsStorage(this);

    const vpc = new Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [{ name: 'public', subnetType: SubnetType.PUBLIC }]
    });

    const cluster = new Cluster(this, 'Cluster', { vpc });
    const logGroup = new LogGroup(this, 'LogGroup', {
      retention: RetentionDays.THREE_DAYS,
      removalPolicy: cdk.RemovalPolicy.DESTROY
    });

    const serviceSecurityGroup = new SecurityGroup(this, 'ServiceSecurityGroup', {
      vpc,
      allowAllOutbound: true
    });
    serviceSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(1935), 'RTMP direct demo access');
    serviceSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(8888), 'HTTP direct demo access');

    let redisUri = 'redis://127.0.0.1:6379';
    if (config.enableElastiCache) {
      const redisSecurityGroup = new SecurityGroup(this, 'RedisSecurityGroup', {
        vpc,
        allowAllOutbound: true
      });
      redisSecurityGroup.addIngressRule(serviceSecurityGroup, Port.tcp(6379), 'Redis from Fargate tasks');

      const subnetGroup = new CfnSubnetGroup(this, 'RedisSubnetGroup', {
        description: 'RTMP demo Redis public-subnet group',
        subnetIds: vpc.publicSubnets.map(subnet => subnet.subnetId)
      });

      const cache = new CfnCacheCluster(this, 'RedisCluster', {
        engine: 'redis',
        cacheNodeType: 'cache.t3.micro',
        numCacheNodes: 1,
        vpcSecurityGroupIds: [redisSecurityGroup.securityGroupId],
        cacheSubnetGroupName: subnetGroup.ref
      });
      cache.addDependency(subnetGroup);
      redisUri = `redis://${cache.attrRedisEndpointAddress}:${cache.attrRedisEndpointPort}`;
    } else if (config.desiredAppCount > 1) {
      throw new Error('managed mode with desiredAppCount > 1 requires -c enableElastiCache=true so tasks share Redis state');
    }

    const nlb = config.enableNlb
      ? new NetworkLoadBalancer(this, 'Nlb', {
          vpc,
          internetFacing: true,
          vpcSubnets: { subnetType: SubnetType.PUBLIC }
        })
      : undefined;
    const advertisedRtmpHost = nlb ? nlb.loadBalancerDnsName : config.rtmpHost;

    const executionRole = new Role(this, 'TaskExecutionRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')]
    });

    const taskRole = new Role(this, 'TaskRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com')
    });
    storage.bucket.grantReadWrite(taskRole);
    taskRole.addToPolicy(new PolicyStatement({
      actions: ['ssm:GetParameter'],
      resources: [`arn:aws:ssm:${this.region}:${this.account}:parameter/rtmp/demo/hmac-secret`]
    }));

    const secretParameter = StringParameter.fromStringParameterName(this, 'HmacSecretParameter', '/rtmp/demo/hmac-secret');

    const taskDefinition = new FargateTaskDefinition(this, 'AppTask', {
      cpu: 256,
      memoryLimitMiB: 1024,
      executionRole,
      taskRole,
      volumes: [{ name: 'hls' }]
    });

    const appContainer = taskDefinition.addContainer('rtmp-app', {
      image: ContainerImage.fromRegistry(config.appImage),
      secrets: {
        RTMP_HMAC_SECRET: Secret.fromSsmParameter(secretParameter)
      },
      environment: {
        REDIS_URI: redisUri,
        RTMP_SERVER_ID: 'managed-fargate-node',
        RTMP_PORT: '1935',
        RTMP_ENDPOINT: `rtmp://${advertisedRtmpHost}:1935/live`,
        RTMP_PLAYBACK_BASE_URL: `https://${storage.distribution.distributionDomainName}`,
        RTMP_HLS_ROOT: '/app/hls'
      },
      logging: new AwsLogDriver({ streamPrefix: 'app', logGroup })
    });
    appContainer.addPortMappings({ containerPort: 8888 }, { containerPort: 1935 });
    appContainer.addMountPoints({ containerPath: '/app/hls', sourceVolume: 'hls', readOnly: false });

    if (!config.enableElastiCache) {
      taskDefinition.addContainer('redis', {
        image: ContainerImage.fromRegistry('redis:7-alpine'),
        memoryReservationMiB: 128,
        logging: new AwsLogDriver({ streamPrefix: 'redis', logGroup })
      }).addPortMappings({ containerPort: 6379 });
    }

    const uploader = taskDefinition.addContainer('hls-uploader', {
      image: ContainerImage.fromRegistry('public.ecr.aws/aws-cli/aws-cli:2'),
      memoryReservationMiB: 96,
      entryPoint: ['sh', '-c'],
      command: [`while true; do aws s3 sync /app/hls s3://${storage.bucket.bucketName}/hls/ --cache-control 'max-age=2'; sleep 2; done`],
      logging: new AwsLogDriver({ streamPrefix: 'uploader', logGroup })
    });
    uploader.addMountPoints({ containerPath: '/app/hls', sourceVolume: 'hls', readOnly: true });

    const service = new FargateService(this, 'AppService', {
      cluster,
      taskDefinition,
      desiredCount: config.desiredAppCount,
      assignPublicIp: true,
      securityGroups: [serviceSecurityGroup],
      vpcSubnets: { subnetType: SubnetType.PUBLIC }
    });

    if (nlb) {
      const listener = nlb.addListener('RtmpListener', { port: 1935 });
      listener.addTargets('RtmpTargets', {
        port: 1935,
        targets: [service.loadBalancerTarget({ containerName: 'rtmp-app', containerPort: 1935 })]
      });
      new cdk.CfnOutput(this, 'NlbRtmpUrl', { value: `rtmp://${nlb.loadBalancerDnsName}:1935/live` });
    }

    if (config.enableAlb) {
      const alb = new ApplicationLoadBalancer(this, 'Alb', {
        vpc,
        internetFacing: true,
        vpcSubnets: { subnetType: SubnetType.PUBLIC }
      });
      const listener = alb.addListener('HttpListener', { port: 80, open: true });
      listener.addTargets('AppTargets', {
        port: 8888,
        targets: [service.loadBalancerTarget({ containerName: 'rtmp-app', containerPort: 8888 })],
        healthCheck: { path: '/', port: '8888' }
      });
      new cdk.CfnOutput(this, 'AlbUrl', { value: `http://${alb.loadBalancerDnsName}/` });
    }

    new cdk.CfnOutput(this, 'PlaybackBaseUrl', { value: `https://${storage.distribution.distributionDomainName}` });
    new cdk.CfnOutput(this, 'HlsBucketName', { value: storage.bucket.bucketName });
    new cdk.CfnOutput(this, 'ManagedModeWarning', {
      value: 'Scale desiredCount to 0 or destroy this stack immediately after demos to avoid hourly service costs.'
    });
  }
}
