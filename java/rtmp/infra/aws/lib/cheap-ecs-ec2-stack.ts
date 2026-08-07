import * as cdk from 'aws-cdk-lib';
import { Stack } from 'aws-cdk-lib';
import { Peer, Port, SecurityGroup, SubnetType, UserData, Vpc } from 'aws-cdk-lib/aws-ec2';
import { AsgCapacityProvider, AwsLogDriver, Cluster, ContainerImage, Ec2Service, Ec2TaskDefinition, EcsOptimizedImage, NetworkMode, Secret } from 'aws-cdk-lib/aws-ecs';
import { ManagedPolicy, PolicyStatement, Role, ServicePrincipal } from 'aws-cdk-lib/aws-iam';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { StringParameter } from 'aws-cdk-lib/aws-ssm';
import { AutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { Construct } from 'constructs';
import { InfraConfig } from './config';
import { createHlsStorage } from './storage-stack';

export interface CheapEcsEc2StackProps extends cdk.StackProps {
  readonly config: InfraConfig;
}

export class CheapEcsEc2Stack extends Stack {
  constructor(scope: Construct, id: string, props: CheapEcsEc2StackProps) {
    super(scope, id, props);

    const { config } = props;
    const storage = createHlsStorage(this);

    const vpc = new Vpc(this, 'Vpc', {
      maxAzs: 1,
      natGateways: 0,
      subnetConfiguration: [{ name: 'public', subnetType: SubnetType.PUBLIC }]
    });

    const securityGroup = new SecurityGroup(this, 'InstanceSecurityGroup', {
      vpc,
      allowAllOutbound: true,
      description: 'Cheap RTMP demo ECS instance access'
    });
    securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), 'HTTP through nginx');
    for (let node = 0; node < config.maxAppCount; node++) {
      securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(1935 + node), `RTMP node ${node + 1}`);
    }
    securityGroup.addIngressRule(Peer.ipv4(config.adminCidr), Port.tcp(22), 'Optional SSH from admin CIDR');

    const cluster = new Cluster(this, 'Cluster', { vpc });
    const userData = UserData.forLinux();
    userData.addCommands(
      'echo ECS_ENABLE_CONTAINER_METADATA=true >> /etc/ecs/ecs.config',
      'echo ECS_AVAILABLE_LOGGING_DRIVERS=["json-file","awslogs"] >> /etc/ecs/ecs.config'
    );

    const instanceRole = new Role(this, 'EcsInstanceRole', {
      assumedBy: new ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonEC2ContainerServiceforEC2Role')
      ]
    });

    const autoScalingGroup = new AutoScalingGroup(this, 'EcsCapacity', {
      vpc,
      instanceType: config.instanceType,
      machineImage: EcsOptimizedImage.amazonLinux2023(),
      role: instanceRole,
      securityGroup,
      userData,
      minCapacity: 1,
      maxCapacity: 1,
      desiredCapacity: 1,
      vpcSubnets: { subnetType: SubnetType.PUBLIC }
    });

    const capacityProvider = new AsgCapacityProvider(this, 'CapacityProvider', {
      autoScalingGroup,
      enableManagedTerminationProtection: false
    });
    cluster.addAsgCapacityProvider(capacityProvider);

    const logGroup = new LogGroup(this, 'LogGroup', {
      retention: RetentionDays.THREE_DAYS,
      removalPolicy: cdk.RemovalPolicy.DESTROY
    });

    const executionRole = new Role(this, 'TaskExecutionRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')]
    });

    const taskRole = new Role(this, 'TaskRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com')
    });
    storage.bucket.grantReadWrite(taskRole);
    executionRole.addToPolicy(new PolicyStatement({
      actions: ['ssm:GetParameter', 'ssm:GetParameters'],
      resources: [`arn:aws:ssm:${this.region}:${this.account}:parameter/rtmp/demo/hmac-secret`]
    }));

    const secretParameter = StringParameter.fromStringParameterName(this, 'HmacSecretParameter', '/rtmp/demo/hmac-secret');

    const redisTask = new Ec2TaskDefinition(this, 'RedisTask', {
      networkMode: NetworkMode.HOST,
      executionRole,
      taskRole
    });
    redisTask.addContainer('redis', {
      image: ContainerImage.fromRegistry('redis:7-alpine'),
      memoryReservationMiB: 128,
      logging: new AwsLogDriver({ streamPrefix: 'redis', logGroup })
    }).addPortMappings({ containerPort: 6379, hostPort: 6379 });
    new Ec2Service(this, 'RedisService', {
      cluster,
      taskDefinition: redisTask,
      desiredCount: 1,
      circuitBreaker: { rollback: false },
      capacityProviderStrategies: [{ capacityProvider: capacityProvider.capacityProviderName, weight: 1 }]
    });

    const nginxConfig = [
      'events {}',
      'http {',
      '  upstream rtmp_app {',
      ...Array.from({ length: config.desiredAppCount }, (_, i) => `    server 127.0.0.1:${8888 + i};`),
      '  }',
      '  server {',
      '    listen 80;',
      '    location / { proxy_pass http://rtmp_app; proxy_set_header Host $host; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; }',
      '  }',
      '}'
    ].join('\n');

    const nginxTask = new Ec2TaskDefinition(this, 'NginxTask', {
      networkMode: NetworkMode.HOST,
      executionRole,
      taskRole
    });
    nginxTask.addContainer('nginx', {
      image: ContainerImage.fromRegistry('nginx:alpine'),
      memoryReservationMiB: 64,
      command: ['sh', '-c', `cat > /etc/nginx/nginx.conf <<'EOF'\n${nginxConfig}\nEOF\nnginx -g 'daemon off;'`],
      logging: new AwsLogDriver({ streamPrefix: 'nginx', logGroup })
    }).addPortMappings({ containerPort: 80, hostPort: 80 });
    new Ec2Service(this, 'NginxService', {
      cluster,
      taskDefinition: nginxTask,
      desiredCount: 1,
      circuitBreaker: { rollback: false },
      capacityProviderStrategies: [{ capacityProvider: capacityProvider.capacityProviderName, weight: 1 }]
    });

    for (let node = 0; node < config.desiredAppCount; node++) {
      const httpPort = 8888 + node;
      const rtmpPort = 1935 + node;
      const hlsVolumeName = `hls-${node + 1}`;
      const taskDefinition = new Ec2TaskDefinition(this, `AppTask${node + 1}`, {
        networkMode: NetworkMode.HOST,
        executionRole,
        taskRole,
        volumes: [{ name: hlsVolumeName }]
      });
      const appContainer = taskDefinition.addContainer('rtmp-app', {
        image: ContainerImage.fromRegistry(config.appImage),
        memoryReservationMiB: 384,
        secrets: {
          RTMP_HMAC_SECRET: Secret.fromSsmParameter(secretParameter)
        },
        environment: {
          MICRONAUT_SERVER_PORT: String(httpPort),
          REDIS_URI: 'redis://127.0.0.1:6379',
          RTMP_SERVER_ID: `cheap-node-${node + 1}`,
          RTMP_PORT: String(rtmpPort),
          RTMP_ENDPOINT: `rtmp://${config.rtmpHost}:${rtmpPort}/live`,
          RTMP_PLAYBACK_BASE_URL: `https://${storage.distribution.distributionDomainName}`,
          RTMP_HLS_ROOT: '/app/hls'
        },
        logging: new AwsLogDriver({ streamPrefix: `app-${node + 1}`, logGroup })
      });
      appContainer.addPortMappings(
        { containerPort: httpPort, hostPort: httpPort },
        { containerPort: rtmpPort, hostPort: rtmpPort }
      );
      appContainer.addMountPoints({ containerPath: '/app/hls', sourceVolume: hlsVolumeName, readOnly: false });

      const uploader = taskDefinition.addContainer('hls-uploader', {
        image: ContainerImage.fromRegistry('public.ecr.aws/aws-cli/aws-cli:2'),
        essential: false,
        memoryReservationMiB: 96,
        entryPoint: ['sh', '-c'],
        command: [`while true; do aws s3 sync /app/hls s3://${storage.bucket.bucketName}/hls/ --cache-control 'max-age=2'; sleep 2; done`],
        logging: new AwsLogDriver({ streamPrefix: `uploader-${node + 1}`, logGroup })
      });
      uploader.addMountPoints({ containerPath: '/app/hls', sourceVolume: hlsVolumeName, readOnly: true });

      new Ec2Service(this, `AppService${node + 1}`, {
        cluster,
        taskDefinition,
        desiredCount: 1,
        circuitBreaker: { rollback: false },
        capacityProviderStrategies: [{ capacityProvider: capacityProvider.capacityProviderName, weight: 1 }]
      });
    }

    new cdk.CfnOutput(this, 'HttpUrl', { value: 'http://<instance-public-dns>/' });
    new cdk.CfnOutput(this, 'AutoScalingGroupName', { value: autoScalingGroup.autoScalingGroupName });
    new cdk.CfnOutput(this, 'PlaybackBaseUrl', { value: `https://${storage.distribution.distributionDomainName}` });
    new cdk.CfnOutput(this, 'HlsBucketName', { value: storage.bucket.bucketName });
    new cdk.CfnOutput(this, 'RtmpHostConfiguration', {
      value: `Redeploy with -c rtmpHost=<instance-public-dns-or-ip>; current value is ${config.rtmpHost}`
    });
  }
}
